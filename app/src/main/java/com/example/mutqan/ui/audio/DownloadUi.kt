package com.example.mutqan.ui.audio

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.ui.components.PaperDialog

/** Whether a surah is kept on the device for the chosen reciter. */
sealed interface DownloadStatus {
    data object None : DownloadStatus
    data class Downloading(val fraction: Float) : DownloadStatus
    data object Done : DownloadStatus
}

/** Download, cancel and (once downloaded) delete, in one button that shows which of them it would do. */
@Composable
fun DownloadButton(status: DownloadStatus, onClick: () -> Unit, modifier: Modifier = Modifier, description: String? = null) {
    IconButton(onClick = onClick, modifier = modifier) {
        when (status) {
            DownloadStatus.None -> Icon(
                Icons.Default.Download,
                contentDescription = description ?: stringResource(R.string.audio_download),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            is DownloadStatus.Downloading -> CircularProgressIndicator(
                progress = { status.fraction.coerceAtLeast(0.04f) },
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            DownloadStatus.Done -> Icon(
                Icons.Default.DownloadDone,
                contentDescription = stringResource(R.string.audio_downloaded),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    PaperDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.audio_delete_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

@Composable
fun DeleteDownloadDialog(surahName: String, reciterName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    PaperDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audio_delete_title)) },
        text = { Text(stringResource(R.string.audio_delete_body, surahName, reciterName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.audio_delete_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}
