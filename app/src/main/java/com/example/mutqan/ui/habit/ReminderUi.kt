package com.example.mutqan.ui.habit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.mutqan.R
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.domain.reminder.DailyReminder
import com.example.mutqan.ui.components.FolioDivider
import com.example.mutqan.ui.components.FolioGroup
import com.example.mutqan.ui.components.IconMedallion
import com.example.mutqan.ui.components.PaperDialog
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperSwitch
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** [minutes] after midnight as the phone shows times: "8:00 م", or "20:00" on a 24-hour phone. */
fun reminderTime(context: Context, locale: Locale, minutes: Int): String {
    val time = LocalTime.of(minutes / 60 % 24, minutes % 60)
    return if (DateFormat.is24HourFormat(context)) "%02d:%02d".format(Locale.ROOT, time.hour, time.minute)
    else time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
}

/**
 * Turns the daily reminder on at a time, or off. Turning it on asks for notifications first where Android needs that,
 * and says when it will come (or that notifications have to be allowed for it to come).
 */
@Composable
fun rememberReminderSwitch(): (on: Boolean, minutes: Int) -> Unit {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    var asked by remember { mutableStateOf<Int?>(null) }
    val denied = stringResource(R.string.reminder_denied)
    val setNote = stringResource(R.string.reminder_set_toast)
    fun turnOn(minutes: Int, allowed: Boolean) {
        scope.launch { DailyReminder.set(context, true, minutes) }
        val note = if (allowed) setNote.format(reminderTime(context, locale, minutes)) else denied
        Toast.makeText(context, note, Toast.LENGTH_LONG).show()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        asked?.let { turnOn(it, granted) }
        asked = null
    }
    return remember(launcher) {
        { on, minutes ->
            when {
                !on -> scope.launch { DailyReminder.set(context, false, minutes) }
                Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED -> {
                    asked = minutes
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> turnOn(minutes, allowed = true)
            }
        }
    }
}

/** Picks the reminder's time on a clock face. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimeDialog(initialMinutes: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60 % 24,
        initialMinute = initialMinutes % 60,
        is24Hour = DateFormat.is24HourFormat(context)
    )
    PaperDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_time_title)) },
        text = {
            Column {
                Text(stringResource(R.string.reminder_time_hint), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.padding(top = 12.dp))
                TimePicker(state = state)
            }
        },
        confirmButton = { PaperLink(text = stringResource(R.string.btn_save), onClick = { onConfirm(state.hour * 60 + state.minute) }) },
        dismissButton = { PaperLink(text = stringResource(R.string.btn_cancel), onClick = onDismiss, soft = true) }
    )
}

/** The daily reminder in Settings: a switch, and its time while it is on. */
@Composable
fun ReminderSettingsRows() {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val prefs = remember(context) { UserPreferencesRepository(context.applicationContext) }
    val settings by prefs.reminder.collectAsState(initial = null)
    val current = settings ?: return
    val switch = rememberReminderSwitch()
    var picking by remember { mutableStateOf(false) }
    val time = reminderTime(context, locale, current.minutes)

    FolioGroup(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = current.on, role = Role.Switch, onValueChange = { on -> switch(on, current.minutes) })
                .heightIn(min = 64.dp)
                .padding(vertical = 10.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconMedallion(Icons.Outlined.NotificationsActive, size = 36.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.reminder_settings_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (current.on) stringResource(R.string.reminder_settings_on, time) else stringResource(R.string.reminder_settings_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PaperSwitch(checked = current.on, onCheckedChange = null)
        }
        if (current.on) {
            FolioDivider(inset = 68.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { picking = true }
                    .heightIn(min = 56.dp)
                    .padding(vertical = 10.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(52.dp))
                Text(stringResource(R.string.reminder_time_label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(time, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    if (picking) {
        ReminderTimeDialog(
            initialMinutes = current.minutes,
            onConfirm = { minutes ->
                picking = false
                switch(true, minutes)
            },
            onDismiss = { picking = false }
        )
    }
}
