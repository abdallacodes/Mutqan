package com.example.mutqan.ui.components

import android.app.Activity
import android.content.Context
import android.annotation.SuppressLint
import android.content.ContextWrapper
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.domain.sync.CloudSync
import com.example.mutqan.domain.sync.SyncStatus
import kotlinx.coroutines.launch

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Sign in with Google and keep this device in step with the account's cloud copy. Shown only in builds that
 * have a Firebase project (see [CloudSync.isConfigured]); the caller decides where it goes in the sheet.
 */
// The failure text is read when a sign-in fails, long after composition, so a language change in between is not a concern.
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun AccountSection() {
    val context = LocalContext.current
    val cloud = remember { CloudSync.get(context) }
    val scope = rememberCoroutineScope()
    val account by cloud.accounts.account.collectAsState()
    val status by cloud.status.collectAsState()
    val lastSync by cloud.lastSyncTime.collectAsState(initial = null)
    var confirmDelete by remember { mutableStateOf(false) }

    SectionLabel(stringResource(R.string.account_title))
    Spacer(Modifier.height(8.dp))

    val signedIn = account
    if (signedIn == null) {
        Text(
            text = stringResource(R.string.account_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        DataActionRow(
            label = stringResource(R.string.account_sign_in),
            icon = Icons.Default.AccountCircle,
            onClick = {
                val activity = context.findActivity() ?: return@DataActionRow
                scope.launch {
                    try {
                        cloud.accounts.signIn(activity)
                        cloud.syncNow()
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.account_sign_in_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    } else {
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = signedIn.name ?: signedIn.email ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (signedIn.name != null && signedIn.email != null) {
                Text(
                    text = signedIn.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val line = when (val s = status) {
                SyncStatus.Syncing -> stringResource(R.string.sync_running)
                is SyncStatus.Failed -> stringResource(R.string.sync_failed, s.message ?: "")
                else -> lastSync?.let {
                    stringResource(
                        R.string.sync_last,
                        DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                    )
                } ?: stringResource(R.string.sync_never)
            }
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = if (status is SyncStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        DataActionRow(
            label = stringResource(R.string.sync_now),
            icon = Icons.Default.Sync,
            onClick = { if (status != SyncStatus.Syncing) scope.launch { cloud.syncNow() } }
        )
        DataActionRow(
            label = stringResource(R.string.sync_delete_cloud),
            icon = Icons.Default.CloudOff,
            onClick = { confirmDelete = true }
        )
        DataActionRow(
            label = stringResource(R.string.account_sign_out),
            icon = Icons.AutoMirrored.Filled.Logout,
            onClick = { scope.launch { cloud.accounts.signOut() } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.sync_delete_title)) },
            text = { Text(stringResource(R.string.sync_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { cloud.deleteCloudCopy() }
                }) { Text(stringResource(R.string.sync_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }
}
