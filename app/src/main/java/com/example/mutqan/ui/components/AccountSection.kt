package com.example.mutqan.ui.components

import android.app.Activity
import android.content.Context
import android.annotation.SuppressLint
import android.content.ContextWrapper
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
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
import com.example.mutqan.domain.sync.OtherDevice
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
    var otherDevice by remember { mutableStateOf<OtherDevice?>(null) }
    val displaced by cloud.displaced.collectAsState(initial = false)

    val backupGate = rememberBackupGate()

    /** Says in a message what the sync that just ended did or why it could not. */
    fun report() {
        val text = when (val s = cloud.status.value) {
            is SyncStatus.Done -> context.getString(outcomeText(s.outcome))
            is SyncStatus.Failed -> context.getString(R.string.sync_failed, s.message ?: "")
            else -> null
        }
        if (text != null) Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    /** Syncs, then says in a message what it did or why it could not. */
    fun runSync(replaceDevice: Boolean = false) {
        scope.launch {
            cloud.syncNow(replaceDevice)
            report()
        }
    }

    /** Erases this device's data and starts from the account's copy (or from nothing), then says what happened. */
    fun runFresh() {
        scope.launch {
            cloud.startFresh()
            report()
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

    // An account works on one device at a time: signing in asks before taking it from another device.
    fun afterSignIn() {
        scope.launch {
            try {
                val other = cloud.otherDeviceInUse()
                if (other != null) {
                    otherDevice = other
                } else {
                    cloud.takeOver()
                    startSync()
                }
            } catch (e: Exception) {
                cloud.signOut()
                Toast.makeText(context, context.getString(R.string.account_sign_in_failed, e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show()
            }
        }
    }

    /** The user chose to use the account here: claim it, then sync. */
    fun useHere() {
        scope.launch {
            try {
                cloud.takeOver()
                startSync()
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
                        if (cloud.accounts.account.value != null) afterSignIn()
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
        if (displaced) {
            // Another device took the account over; nothing is synced from here until the user decides.
            Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.session_displaced),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                PaperButton(stringResource(R.string.session_use_here), onClick = { useHere() }, modifier = Modifier.fillMaxWidth())
            }
        } else {
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
        }
        DataActionRow(
            label = stringResource(R.string.account_sign_out),
            icon = Icons.AutoMirrored.Filled.Logout,
            onClick = { scope.launch { cloud.signOut() } }
        )
    }

    firstSync?.let { choice ->
        FirstSyncDialog(
            choice = choice,
            onMerge = {
                firstSync = null
                runSync()
            },
            // Erases the device's data, so a backup comes first; the question stays up until it is saved.
            onReplace = {
                backupGate.require {
                    firstSync = null
                    if (choice.cloudExists) runSync(replaceDevice = true) else runFresh()
                }
            },
            onCancel = {
                firstSync = null
                scope.launch { cloud.accounts.signOut() }
            }
        )
    }

    otherDevice?.let { other ->
        OtherDeviceDialog(
            other = other,
            onUseHere = {
                otherDevice = null
                useHere()
            },
            onCancel = {
                otherDevice = null
                scope.launch { cloud.signOut() }
            }
        )
    }

    if (confirmDelete) {
        PaperDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.sync_delete_title)) },
            text = { Text(stringResource(R.string.sync_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    backupGate.require { scope.launch { cloud.deleteCloudCopy() } }
                }) { Text(stringResource(R.string.sync_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }
}

/** Asked when the account being signed in is in use on another device: take it over, or leave it there. */
@Composable
private fun OtherDeviceDialog(other: OtherDevice, onUseHere: () -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = {}) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.other_device_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.other_device_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                other.lastActive?.let {
                    val ago = DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                    Text(
                        text = stringResource(R.string.other_device_last_active, ago),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(4.dp))
                PaperButton(stringResource(R.string.other_device_use_here), onClick = onUseHere, modifier = Modifier.fillMaxWidth())
                PaperLink(
                    text = stringResource(R.string.btn_cancel),
                    onClick = onCancel,
                    soft = true,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

/**
 * Asked when an account that already has data in the cloud signs in on a device that has data too: keep both
 * (the safe choice, offered first), or let the account's data take the place of this device's.
 */
@Composable
private fun FirstSyncDialog(choice: FirstSync, onMerge: () -> Unit, onReplace: () -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = {}) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(if (choice.cloudExists) R.string.first_sync_title else R.string.first_sync_new_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(if (choice.cloudExists) R.string.first_sync_intro else R.string.first_sync_new_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (choice.cloudExists) Text(
                        text = stringResource(R.string.first_sync_cloud_count, choice.cloudEntries),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.first_sync_device_count, choice.deviceEntries),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (choice.otherAccount) {
                    Text(
                        text = stringResource(R.string.first_sync_other_account),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Spacer(Modifier.height(4.dp))
                PaperButton(
                    stringResource(if (choice.cloudExists) R.string.first_sync_merge else R.string.first_sync_upload),
                    onClick = onMerge,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(if (choice.cloudExists) R.string.first_sync_merge_hint else R.string.first_sync_upload_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(4.dp))
                PaperOutlinedButton(
                    stringResource(if (choice.cloudExists) R.string.first_sync_replace else R.string.first_sync_fresh),
                    onClick = onReplace,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(if (choice.cloudExists) R.string.first_sync_replace_hint else R.string.first_sync_fresh_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )

                PaperLink(
                    text = stringResource(R.string.btn_cancel),
                    onClick = onCancel,
                    soft = true,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}
