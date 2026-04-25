package com.example.qmemo.ui.components

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.qmemo.domain.BackupManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

sealed class BackupUiEvent {
    data class Success(val message: String) : BackupUiEvent()
    data class Error(val message: String) : BackupUiEvent()
}

class SettingsViewModel(private val backupManager: BackupManager) : ViewModel() {

    private val _events = MutableSharedFlow<BackupUiEvent>()
    val events: SharedFlow<BackupUiEvent> = _events

    fun exportData(uri: Uri) {
        viewModelScope.launch {
            val result = backupManager.exportData(uri)
            if (result.isSuccess) {
                _events.emit(BackupUiEvent.Success("Backup exported successfully"))
            } else {
                _events.emit(BackupUiEvent.Error("Failed to export backup: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            val result = backupManager.importData(uri)
            if (result.isSuccess) {
                _events.emit(BackupUiEvent.Success("Data restored successfully"))
            } else {
                _events.emit(BackupUiEvent.Error("Failed to restore data: ${result.exceptionOrNull()?.message}"))
            }
        }
    }
}

class SettingsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(BackupManager(context.applicationContext)) as T
}
