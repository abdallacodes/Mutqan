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
import com.example.mutqan.domain.sync.FirstSync
import com.example.mutqan.domain.sync.SyncOutcome
import com.example.mutqan.domain.sync.SyncStatus
import kotlinx.coroutines.launch

private fun outcomeText(outcome: SyncOutcome): Int = when (outcome) {
    SyncOutcome.NOTHING_TO_DO -> R.string.sync_outcome_nothing
    SyncOutcome.PUSHED -> R.string.sync_outcome_pushed
    SyncOutcome.PULLED -> R.string.sync_outcome_pulled
    SyncOutcome.MERGED -> R.string.sync_outcome_merged
}

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
    var firstSync by remember { mutableStateOf<FirstSync?>(null) }

    /** Syncs, then says in a message what it did or why it could not. */
    fun runSync(replaceDevice: Boolean = false) {
        scope.launch {
            cloud.syncNow(replaceDevice)
            val text = when (val s = cloud.status.value) {
                is SyncStatus.Done -> context.getString(outcomeText(s.outcome))
                is SyncStatus.Failed -> context.getString(R.string.sync_failed, s.message ?: "")
                else -> null
            }
            if (text != null) Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    // A new account on this device with data on both sides asks first; everything else syncs straight away.
    fun startSync() {
        scope.launch {
            try {
                val choice = cloud.firstSyncChoice()
                if (choice != null) firstSync = choice else runSync()
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.sync_failed, e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show()
            }
        }
    }

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
                        if (cloud.accounts.account.value != null) startSync()
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
                    val ago = DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                    val done = (status as? SyncStatus.Done)?.let { d -> stringResource(outcomeText(d.outcome)) + "\n" }.orEmpty()
                    done + stringResource(R.string.sync_last, ago)
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
            onClick = { if (status != SyncStatus.Syncing) startSync() }
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

    firstSync?.let { choice ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.first_sync_title)) },
            text = {
                Text(
                    stringResource(R.string.first_sync_body, choice.cloudEntries, choice.deviceEntries) +
                        if (choice.otherAccount) "\n\n" + stringResource(R.string.first_sync_other_account) else ""
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    firstSync = null
                    runSync()
                }) { Text(stringResource(R.string.first_sync_merge)) }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = {
                        firstSync = null
                        runSync(replaceDevice = true)
                    }) { Text(stringResource(R.string.first_sync_replace), color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = {
                        firstSync = null
                        scope.launch { cloud.accounts.signOut() }
                    }) { Text(stringResource(R.string.btn_cancel)) }
                }
            }
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
