package com.example.mutqan.ui.update

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.domain.update.UpdateManager
import com.example.mutqan.domain.update.UpdateManager.Problem
import com.example.mutqan.domain.update.UpdateManager.State
import com.example.mutqan.ui.components.Folio
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperProgress
import com.example.mutqan.ui.components.PaperSwitch

/** Whether Today should show the update card: an update is offered, being downloaded, ready, or its download failed. */
val State.isOffered: Boolean
    get() = this is State.Available || this is State.Downloading || this is State.Ready || (this is State.Failed && info != null)

@Composable
private fun problemText(problem: Problem): String = stringResource(
    when (problem) {
        Problem.NETWORK -> R.string.update_failed_network
        Problem.BAD_FILE -> R.string.update_failed_file
        Problem.STORAGE -> R.string.update_failed_storage
    }
)

/** Opens the installer; when Android does not yet allow this app to install, sends the user to that setting first. */
private fun installNow(manager: UpdateManager, context: Context) {
    if (!manager.canInstall()) {
        Toast.makeText(context, R.string.update_allow_install, Toast.LENGTH_LONG).show()
        manager.allowInstalls(context)
    } else if (!manager.install(context)) {
        Toast.makeText(context, R.string.update_install_failed, Toast.LENGTH_LONG).show()
    }
}

/** The card on Today: a new version is out, with the one button that takes it forward (update, install, or try again). */
@Composable
fun UpdateCard(state: State, manager: UpdateManager, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val info = when (state) {
        is State.Available -> state.info
        is State.Downloading -> state.info
        is State.Ready -> state.info
        is State.Failed -> state.info
        else -> null
    } ?: return
    Folio(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.update_available, info.version),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when (state) {
                        is State.Downloading -> stringResource(R.string.update_downloading, (state.fraction * 100).toInt())
                        is State.Ready -> stringResource(R.string.update_ready)
                        is State.Failed -> problemText(state.problem)
                        else -> stringResource(R.string.update_available_body)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        when (state) {
            is State.Downloading -> PaperProgress(fraction = state.fraction, modifier = Modifier.fillMaxWidth())
            is State.Ready -> PaperButton(
                text = stringResource(R.string.update_install),
                onClick = { installNow(manager, context) },
                modifier = Modifier.fillMaxWidth()
            )
            is State.Failed -> PaperButton(
                text = stringResource(R.string.update_retry),
                onClick = manager::download,
                modifier = Modifier.fillMaxWidth()
            )
            else -> PaperButton(
                text = stringResource(R.string.update_btn),
                onClick = manager::download,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** The line in Settings → About: check for an update, then update and install it, with what happened under it. */
@Composable
fun UpdateSettingsRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember { UpdateManager.get(context) }
    val state by manager.state.collectAsState()
    val autoCheck by manager.autoCheck.collectAsState()

    var label = stringResource(R.string.update_check)
    var note: String? = null
    var onClick: (() -> Unit)? = { manager.check() }
    when (val s = state) {
        is State.Checking -> { if (!s.quiet) note = stringResource(R.string.update_checking); onClick = if (s.quiet) onClick else null }
        is State.UpToDate -> note = stringResource(R.string.update_latest)
        is State.Available -> { label = stringResource(R.string.update_to, s.info.version); onClick = manager::download }
        is State.Downloading -> {
            label = stringResource(R.string.update_downloading, (s.fraction * 100).toInt())
            onClick = null
        }
        is State.Ready -> { label = stringResource(R.string.update_install_version, s.info.version); onClick = { installNow(manager, context) } }
        is State.Failed -> {
            note = problemText(s.problem)
            if (s.info != null) { label = stringResource(R.string.update_retry); onClick = manager::download }
        }
        State.Idle -> Unit
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = onClick != null, role = Role.Button, onClick = { onClick?.invoke() })
                .heightIn(min = 48.dp)
                .padding(horizontal = 4.dp, vertical = 10.dp)
        )
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 4.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = autoCheck, role = Role.Switch, onValueChange = manager::setAutoCheck)
                .heightIn(min = 48.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.update_auto),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            PaperSwitch(checked = autoCheck, onCheckedChange = null)
        }
    }
}
