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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.domain.sync.Choice
import com.example.mutqan.domain.sync.CloudSync
import com.example.mutqan.domain.sync.FirstSync
import com.example.mutqan.domain.sync.OtherDevice
import com.example.mutqan.domain.sync.SyncOutcome
import com.example.mutqan.domain.sync.SyncStatus
import kotlinx.coroutines.flow.first
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
    val linked by cloud.linked.collectAsState(initial = false)

    /** Says in a message what the sync that just ended did or why it could not. */
    fun report() {
        val text = when (val s = cloud.status.value) {
            is SyncStatus.Done -> context.getString(outcomeText(s.outcome))
            is SyncStatus.Failed -> context.getString(R.string.sync_failed)
            SyncStatus.CopyFailed -> context.getString(R.string.copy_failed)
            SyncStatus.CloudGone -> context.getString(R.string.sync_cloud_gone)
            SyncStatus.CloudDamaged -> context.getString(R.string.sync_cloud_damaged)
            else -> null
        }
        if (text != null) Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    /** Only what went wrong: when a join or a deletion works, the dialog of [SyncNoticeHost] says so, with where the copy is. */
    fun reportProblem(joining: Boolean = false) {
        val status = cloud.status.value
        if (status is SyncStatus.Failed && joining) {
            Toast.makeText(context, context.getString(R.string.sync_set_up_failed), Toast.LENGTH_LONG).show()
        } else if (status !is SyncStatus.Done && status != SyncStatus.Idle) {
            report()
        }
    }

    /** Syncs, then says in a message what it did or why it could not. */
    fun runSync() {
        scope.launch {
            cloud.syncNow()
            report()
        }
    }

    /** Joins the account the way the user chose. */
    fun runJoin(choice: Choice) {
        scope.launch {
            cloud.enableSync(choice)
            reportProblem(joining = true)
        }
    }

    // Looks at both sides and changes nothing. A device with data asks which side wins; one with nothing just takes the account's data.
    fun chooseData() {
        scope.launch {
            try {
                val choice = cloud.setupChoice()
                if (choice != null) firstSync = choice else runJoin(Choice.CLOUD_WINS)
            } catch (e: Exception) {
                android.util.Log.w("AccountSection", "Could not look at the cloud copy", e)
                Toast.makeText(context, context.getString(R.string.sync_set_up_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    // An account works on one device at a time: signing in asks before taking it from another device, then comes to the data.
    fun setUpSync() {
        scope.launch {
            try {
                val other = cloud.otherDeviceInUse()
                if (other != null) otherDevice = other else chooseData()
            } catch (e: Exception) {
                android.util.Log.w("AccountSection", "Could not look at the account", e)
                Toast.makeText(context, context.getString(R.string.sync_set_up_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun afterSignIn() {
        scope.launch {
            try {
                val other = cloud.otherDeviceInUse()
                if (other != null) otherDevice = other else chooseData()
            } catch (e: Exception) {
                android.util.Log.w("AccountSection", "Sign-in could not be completed", e)
                cloud.signOut()
                Toast.makeText(context, context.getString(R.string.account_sign_in_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Another device had the account and took it over; the user chose to use it here again. */
    fun useHere() {
        scope.launch {
            try {
                cloud.takeOver()
                if (cloud.linked.first()) runSync() else setUpSync()
            } catch (e: Exception) {
                android.util.Log.w("AccountSection", "Could not take the account back", e)
                Toast.makeText(context, context.getString(if (linked) R.string.sync_failed else R.string.sync_set_up_failed), Toast.LENGTH_LONG).show()
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
                        cloud.signIn(activity)
                        if (cloud.accounts.account.value != null) afterSignIn()
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.account_sign_in_failed), Toast.LENGTH_LONG).show()
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
                is SyncStatus.Failed -> stringResource(if (linked) R.string.sync_failed else R.string.sync_set_up_failed)
                SyncStatus.CopyFailed -> stringResource(R.string.copy_failed)
                SyncStatus.CloudGone -> stringResource(R.string.sync_cloud_gone)
                SyncStatus.CloudDamaged -> stringResource(R.string.sync_cloud_damaged)
                else -> if (!linked) stringResource(R.string.sync_off) else lastSync?.let {
                    val now = System.currentTimeMillis()
                    val done = (status as? SyncStatus.Done)?.let { d -> stringResource(outcomeText(d.outcome)) + "\n" }.orEmpty()
                    // Under a minute DateUtils says "0 minutes ago".
                    done + if (now - it < DateUtils.MINUTE_IN_MILLIS) stringResource(R.string.sync_last_now)
                    else stringResource(R.string.sync_last, DateUtils.getRelativeTimeSpanString(it, now, DateUtils.MINUTE_IN_MILLIS).toString())
                } ?: stringResource(R.string.sync_never)
            }
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = if (status is SyncStatus.Failed || status == SyncStatus.CopyFailed || status == SyncStatus.CloudGone || status == SyncStatus.CloudDamaged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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
        } else if (linked) {
            DataActionRow(
                label = stringResource(R.string.sync_now),
                icon = Icons.Default.Sync,
                onClick = { if (status != SyncStatus.Syncing) runSync() }
            )
            DataActionRow(
                label = stringResource(R.string.sync_delete_cloud),
                icon = Icons.Default.CloudOff,
                onClick = { confirmDelete = true }
            )
        } else {
            // Signed in but not set up: nothing is sent or received until the user chooses how this device joins the account.
            DataActionRow(
                label = stringResource(R.string.sync_set_up),
                icon = Icons.Default.Sync,
                onClick = { if (status != SyncStatus.Syncing) setUpSync() }
            )
        }
        DataActionRow(
            label = stringResource(R.string.account_sign_out),
            icon = Icons.AutoMirrored.Filled.Logout,
            onClick = { scope.launch { cloud.signOut() } }
        )
    }

    firstSync?.takeIf { it.cloudDamaged }?.let { choice ->
        DamagedCopyDialog(
            deviceEntries = choice.deviceEntries,
            onReplace = {
                firstSync = null
                runJoin(Choice.DEVICE_WINS)
            },
            onCancel = {
                firstSync = null
                scope.launch { cloud.signOut() }
            }
        )
    }

    firstSync?.takeIf { !it.cloudDamaged }?.let { choice ->
        FirstSyncDialog(
            choice = choice,
            onCloudWins = {
                firstSync = null
                runJoin(Choice.CLOUD_WINS)
            },
            onDeviceWins = {
                firstSync = null
                runJoin(Choice.DEVICE_WINS)
            },
            onCancel = {
                firstSync = null
                scope.launch { cloud.signOut() }
            }
        )
    }

    otherDevice?.let { other ->
        OtherDeviceDialog(
            other = other,
            onUseHere = {
                otherDevice = null
                chooseData()
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
                    scope.launch {
                        cloud.deleteCloudCopy()
                        reportProblem()
                    }
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
 * The account's cloud copy has pieces missing, so it cannot be read or restored. The only way on is to delete it and
 * upload this device's data; nothing is deleted or uploaded until the user says so.
 */
@Composable
private fun DamagedCopyDialog(deviceEntries: Int, onReplace: () -> Unit, onCancel: () -> Unit) {
    PaperDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.damaged_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.damaged_body))
                Text(
                    text = stringResource(
                        R.string.first_sync_device_count,
                        pluralStringResource(R.plurals.count_revision_entries, deviceEntries, deviceEntries)
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        },
        confirmButton = { TextButton(onClick = onReplace) { Text(stringResource(R.string.damaged_confirm), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

/**
 * Asked when this device holds data as it joins an account: the cloud copy replaces the device's data, or the device's
 * data replaces the cloud copy. There is no third way; whichever is chosen, a copy of what is replaced is saved on the
 * phone first. Nothing is synced until one of the two is chosen and has worked.
 */
@Composable
private fun FirstSyncDialog(choice: FirstSync, onCloudWins: () -> Unit, onDeviceWins: () -> Unit, onCancel: () -> Unit) {
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
                        text = stringResource(
                            R.string.first_sync_cloud_count,
                            pluralStringResource(R.plurals.count_revision_entries, choice.cloudEntries, choice.cloudEntries)
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            R.string.first_sync_device_count,
                            pluralStringResource(R.plurals.count_revision_entries, choice.deviceEntries, choice.deviceEntries)
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(4.dp))
                PaperOutlinedButton(
                    stringResource(if (choice.cloudExists) R.string.first_sync_replace else R.string.first_sync_fresh),
                    onClick = onCloudWins,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(if (choice.cloudExists) R.string.first_sync_replace_hint else R.string.first_sync_fresh_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(Modifier.height(4.dp))
                PaperOutlinedButton(
                    stringResource(if (choice.cloudExists) R.string.first_sync_overwrite else R.string.first_sync_upload),
                    onClick = onDeviceWins,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(if (choice.cloudExists) R.string.first_sync_overwrite_hint else R.string.first_sync_upload_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (choice.cloudExists) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
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
