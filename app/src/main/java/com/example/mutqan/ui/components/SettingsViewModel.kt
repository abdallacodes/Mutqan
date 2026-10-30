package com.example.mutqan.ui.components

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mutqan.R
import com.example.mutqan.domain.BackupManager
import com.example.mutqan.quran.DownloadState
import com.example.mutqan.quran.MushafDownloadManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class BackupUiEvent {
    data class Success(val message: UiText) : BackupUiEvent()
    data class Error(val message: UiText) : BackupUiEvent()
}

class SettingsViewModel(
    private val backupManager: BackupManager,
    private val downloadManager: MushafDownloadManager
) : ViewModel() {

    private val _events = MutableSharedFlow<BackupUiEvent>()
    val events: SharedFlow<BackupUiEvent> = _events

    val downloadState: StateFlow<DownloadState> = downloadManager.downloadState

    fun startMushafDownload() = downloadManager.startDownload()

    fun exportData(uri: Uri) {
        viewModelScope.launch {
            val result = backupManager.exportData(uri)
            if (result.isSuccess) {
                _events.emit(BackupUiEvent.Success(UiText(R.string.backup_success)))
            } else {
                _events.emit(BackupUiEvent.Error(UiText(R.string.backup_error_detail, result.exceptionOrNull()?.message ?: "")))
            }
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            val result = backupManager.importData(uri)
            if (result.isSuccess) {
                _events.emit(BackupUiEvent.Success(UiText(R.string.restore_success)))
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
            MushafDownloadManager(appContext)
        ) as T
    }
}
