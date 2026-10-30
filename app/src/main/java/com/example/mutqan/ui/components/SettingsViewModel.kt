package com.example.mutqan.ui.components

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.R
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.data.local.AppDatabase
import com.example.mutqan.domain.BackupManager
import com.example.mutqan.domain.PasswordRequiredException
import com.example.mutqan.domain.WrongPasswordException
import com.example.mutqan.quran.DownloadState
import com.example.mutqan.quran.MushafDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class BackupUiEvent {
    data class Success(val message: UiText) : BackupUiEvent()
    data class Error(val message: UiText) : BackupUiEvent()

    /** The chosen backup file is encrypted; ask for its password and import [uri] again. */
    data class NeedsPassword(val uri: Uri) : BackupUiEvent()
}

class SettingsViewModel(
    private val backupManager: BackupManager,
    private val downloadManager: MushafDownloadManager,
    private val database: AppDatabase,
    private val preferences: UserPreferencesRepository
) : ViewModel() {

    val appLockEnabled: StateFlow<Boolean> =
        preferences.appLockEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setAppLock(enabled: Boolean) {
        viewModelScope.launch { preferences.setAppLockEnabled(enabled) }
    }

    /** Permanently deletes everything the user recorded on this device. */
    fun deleteAllData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { database.clearAllTables() }
            _events.emit(BackupUiEvent.Success(UiText(R.string.delete_all_done)))
        }
    }

    private val _events = MutableSharedFlow<BackupUiEvent>()
    val events: SharedFlow<BackupUiEvent> = _events

    val downloadState: StateFlow<DownloadState> = downloadManager.downloadState

    fun startMushafDownload() = downloadManager.startDownload()

    fun exportData(uri: Uri, password: String? = null) {
        viewModelScope.launch {
            val result = backupManager.exportData(uri, password)
            if (result.isSuccess) {
                _events.emit(BackupUiEvent.Success(UiText(R.string.backup_success)))
            } else {
                _events.emit(BackupUiEvent.Error(UiText(R.string.backup_error_detail, result.exceptionOrNull()?.message ?: "")))
            }
        }
    }

    fun importData(uri: Uri, password: String? = null) {
        viewModelScope.launch {
            val result = backupManager.importData(uri, password)
            val error = result.exceptionOrNull()
            if (result.isSuccess) {
                _events.emit(BackupUiEvent.Success(UiText(R.string.restore_success)))
            } else if (error is PasswordRequiredException) {
                _events.emit(BackupUiEvent.NeedsPassword(uri))
            } else if (error is WrongPasswordException) {
                _events.emit(BackupUiEvent.Error(UiText(R.string.restore_wrong_password)))
                _events.emit(BackupUiEvent.NeedsPassword(uri))
            } else {
                _events.emit(BackupUiEvent.Error(UiText(R.string.restore_error_detail, result.exceptionOrNull()?.message ?: "")))
            }
        }
    }
}

class SettingsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val appContext = context.applicationContext
        return SettingsViewModel(
            BackupManager(appContext),
            MushafDownloadManager(appContext),
            AppDatabase.getInstance(appContext),
            UserPreferencesRepository(appContext)
        ) as T
    }
}
