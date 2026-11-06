package com.example.mutqan.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.domain.sync.CloudSync
import com.example.mutqan.domain.sync.NoticeKind

/**
 * Says what just happened once a join, a cloud deletion or a device deletion is complete: what changed, that a copy of
 * what was replaced or deleted is saved on this phone (and where), and, for a join, that syncing is on. It lives at
 * the root of the app because the screen that started the action may be gone by then, as after deleting the device's data.
 */
@Composable
fun SyncNoticeHost() {
    val context = LocalContext.current
    val cloud = remember { CloudSync.get(context) }
    val notice by cloud.notice.collectAsState()
    val shown = notice ?: return

    val title = when (shown.kind) {
        NoticeKind.SYNC_ON_CLOUD, NoticeKind.SYNC_ON_DEVICE -> R.string.notice_title_sync
        NoticeKind.CLOUD_DELETED -> R.string.notice_title_cloud_deleted
        NoticeKind.DEVICE_RESET -> R.string.notice_title_device_reset
    }
    val what = when (shown.kind) {
        NoticeKind.SYNC_ON_CLOUD -> if (shown.cloudExisted) R.string.notice_sync_cloud else R.string.notice_sync_cloud_empty
        NoticeKind.SYNC_ON_DEVICE -> R.string.notice_sync_device
        NoticeKind.CLOUD_DELETED -> R.string.notice_cloud_deleted
        NoticeKind.DEVICE_RESET -> R.string.notice_device_reset
    }
    val savedLine = when (shown.kind) {
        NoticeKind.SYNC_ON_CLOUD -> R.string.notice_copy_replaced_device
        NoticeKind.SYNC_ON_DEVICE -> R.string.notice_copy_replaced_cloud
        NoticeKind.CLOUD_DELETED, NoticeKind.DEVICE_RESET -> R.string.notice_copy_deleted
    }

    PaperDialog(
        onDismissRequest = { cloud.dismissNotice() },
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(what), style = MaterialTheme.typography.bodyMedium)
                val copy = shown.copy
                if (copy != null) {
                    Text(stringResource(savedLine), style = MaterialTheme.typography.bodyMedium)
                    // A path reads left to right whatever the language of the screen.
                    Text(
                        text = copy.where,
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.notice_copy_restore), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.notice_copy_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = { cloud.dismissNotice() }) { Text(stringResource(R.string.btn_ok)) } }
    )
}
