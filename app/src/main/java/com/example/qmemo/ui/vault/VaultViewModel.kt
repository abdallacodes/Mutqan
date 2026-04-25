package com.example.qmemo.ui.vault

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.qmemo.data.local.AppDatabase
import com.example.qmemo.data.local.dao.GroupWithCount
import com.example.qmemo.data.local.dao.QuranDao
import com.example.qmemo.data.local.entity.SimilarityGroupEntity
import com.example.qmemo.data.local.entity.VaultFolderEntity
import com.example.qmemo.domain.VaultSharingManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class VaultUiEvent {
    data class Success(val message: String) : VaultUiEvent()
    data class Error(val message: String) : VaultUiEvent()
}

class VaultViewModel(
    private val dao: QuranDao,
    private val sharingManager: VaultSharingManager
) : ViewModel() {

    private val _currentFolderId = MutableStateFlow<Int?>(null)
    val currentFolderId: StateFlow<Int?> = _currentFolderId.asStateFlow()

    private val _currentFolderName = MutableStateFlow<String?>(null)
    val currentFolderName: StateFlow<String?> = _currentFolderName.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val folders: StateFlow<List<VaultFolderEntity>> = _currentFolderId
        .flatMapLatest { dao.getFoldersByParent(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val groups: StateFlow<List<GroupWithCount>> = _currentFolderId
        .flatMapLatest { dao.getGroupsByFolder(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _events = MutableSharedFlow<VaultUiEvent>()
    val events: SharedFlow<VaultUiEvent> = _events

    fun navigateToFolder(folder: VaultFolderEntity?) {
        _currentFolderId.value = folder?.id
        _currentFolderName.value = folder?.name
    }

    fun navigateBack() {
        val parentId = _currentFolderId.value ?: return
        viewModelScope.launch {
            val folder = dao.getFolderById(parentId)
            val grandParent = folder?.parentId?.let { dao.getFolderById(it) }
            _currentFolderId.value = folder?.parentId
            _currentFolderName.value = grandParent?.name
        }
    }

    fun addFolder(name: String) {
        viewModelScope.launch {
            if (dao.getFolderByName(name, _currentFolderId.value) != null) {
                _events.emit(VaultUiEvent.Error("Folder already exists in this directory"))
                return@launch
            }
            dao.insertFolder(VaultFolderEntity(name = name, parentId = _currentFolderId.value))
        }
    }

    fun renameFolder(folder: VaultFolderEntity, newName: String) {
        viewModelScope.launch {
            if (dao.getFolderByName(newName, folder.parentId) != null) {
                _events.emit(VaultUiEvent.Error("Folder name already exists"))
                return@launch
            }
            dao.updateFolder(folder.copy(name = newName))
            if (_currentFolderId.value == folder.id) {
                _currentFolderName.value = newName
            }
        }
    }

    fun deleteFolder(folder: VaultFolderEntity) {
        viewModelScope.launch {
            dao.deleteFolder(folder)
        }
    }

    fun deleteGroup(item: GroupWithCount) {
        viewModelScope.launch { dao.deleteSimilarityGroup(item.group) }
    }

    fun moveFolder(folder: VaultFolderEntity, newParentId: Int?) {
        viewModelScope.launch {
            if (folder.id == newParentId) return@launch // Can't move into itself
            dao.updateFolder(folder.copy(parentId = newParentId))
            _events.emit(VaultUiEvent.Success("Folder moved"))
        }
    }

    fun moveGroup(group: SimilarityGroupEntity, newFolderId: Int?) {
        viewModelScope.launch {
            dao.updateSimilarityGroup(group.copy(folderId = newFolderId))
            _events.emit(VaultUiEvent.Success("Group moved"))
        }
    }

    fun exportVault(uri: Uri, folderId: Int?) {
        viewModelScope.launch {
            val result = sharingManager.exportVault(uri, folderId)
            if (result.isSuccess) {
                _events.emit(VaultUiEvent.Success("Vault exported successfully"))
            } else {
                _events.emit(VaultUiEvent.Error("Export failed: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun importVault(uri: Uri, folderName: String) {
        viewModelScope.launch {
            if (dao.getFolderByName(folderName, _currentFolderId.value) != null) {
                _events.emit(VaultUiEvent.Error("Folder name already exists"))
                return@launch
            }
            val result = sharingManager.importVault(uri, folderName)
            if (result.isSuccess) {
                _events.emit(VaultUiEvent.Success("Vault imported into folder \"$folderName\""))
            } else {
                _events.emit(VaultUiEvent.Error("Import failed: ${result.exceptionOrNull()?.message}"))
            }
        }
    }
}

class VaultViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getInstance(context)
        return VaultViewModel(
            db.quranDao(),
            VaultSharingManager(context.applicationContext)
        ) as T
    }
}
