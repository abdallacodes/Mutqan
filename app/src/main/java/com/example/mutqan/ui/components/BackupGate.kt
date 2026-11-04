package com.example.mutqan.ui.components

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.mutqan.R
import com.example.mutqan.domain.BackupManager
import com.example.mutqan.domain.sync.SyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stands in front of anything that can lose the user's data: [require] asks for a backup file to be saved first and
 * runs the action only once the file is written. When the device holds no data there is nothing to lose, so the
 * action runs at once.
 */
class BackupGate internal constructor(private val start: (action: () -> Unit) -> Unit) {
    fun require(action: () -> Unit) = start(action)
}

// The messages are read when the file has been written, long after composition, so a language change in between is not a concern.
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun rememberBackupGate(): BackupGate {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backup = remember { BackupManager(context) }
    // The action waiting for its backup, and whether the explanation is on screen.
    val waiting = remember { mutableStateOf<(() -> Unit)?>(null) }
    val asking = remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val action = waiting.value
        waiting.value = null
        if (uri != null && action != null) {
            scope.launch {
                val result = backup.exportData(uri)
                if (result.isSuccess) {
                    Toast.makeText(context, context.getString(R.string.backup_success), Toast.LENGTH_SHORT).show()
                    action()
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.backup_error_detail, result.exceptionOrNull()?.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    val gate = remember {
        BackupGate { action ->
            scope.launch {
                val hasData = withContext(Dispatchers.IO) { !SyncEngine.isEmpty(backup.buildSnapshot().toString()) }
                if (hasData) {
                    waiting.value = action
                    asking.value = true
                } else {
                    action()
                }
            }
        }
    }

    if (asking.value) {
        PaperDialog(
            onDismissRequest = { asking.value = false; waiting.value = null },
            title = { Text(stringResource(R.string.backup_gate_title)) },
            text = { Text(stringResource(R.string.backup_gate_body), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    asking.value = false
                    saveLauncher.launch("mutqan_backup_${System.currentTimeMillis()}.json")
                }) { Text(stringResource(R.string.backup_gate_save)) }
            },
            dismissButton = {
                TextButton(onClick = { asking.value = false; waiting.value = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
    return gate
}
