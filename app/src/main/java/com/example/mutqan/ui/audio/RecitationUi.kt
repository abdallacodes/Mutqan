package com.example.mutqan.ui.audio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.domain.audio.RecitationController
import com.example.mutqan.domain.audio.RecitationPlayer
import com.example.mutqan.domain.audio.Reciter
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.components.PaperChip
import kotlinx.coroutines.launch

private var askedForNotifications = false

/**
 * A function that, the first time it is called, asks for permission to show the playback notification (Android 13 and up).
 * Playing does not wait for the answer; without it the sound still plays, only the notification is missing.
 */
@Composable
fun rememberAskForNotifications(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return remember(launcher) {
        {
            if (Build.VERSION.SDK_INT >= 33 && !askedForNotifications &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                askedForNotifications = true
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

/** "Surah · verse" for the verse sounding, or an empty line when none is. */
@Composable
fun nowPlayingLabel(ayahId: Int?): String {
    val context = LocalContext.current
    val index = remember(context) { QuranRepository.getInstance(context).ayahIndex }
    if (ayahId == null || ayahId !in index) return ""
    return stringResource(R.string.saved_ref, SurahData.nameOf(index.surahOf(ayahId)), index.ayahOf(ayahId))
}

/** The surah being recited, or null when nothing is. */
@Composable
fun playingSurah(state: RecitationPlayer.State): Int? {
    val context = LocalContext.current
    val index = remember(context) { QuranRepository.getInstance(context).ayahIndex }
    val id = state.ayahId ?: return null
    return if (state.active && id in index) index.surahOf(id) else null
}

/** The controls of the recitation under way: verse before and after, pause, the settings and stop. */
@Composable
fun RecitationBar(
    state: RecitationPlayer.State,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceContainer,
    ink: Color = MaterialTheme.colorScheme.onSurface,
    muted: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val context = LocalContext.current
    val player = remember(context) { RecitationPlayer.get(context) }
    val reciterId by remember(context) { UserPreferencesRepository(context.applicationContext).reciterId }.collectAsState(initial = "")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onOpenSettings)
                .padding(vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.audio_settings), tint = muted, modifier = Modifier.size(16.dp))
                Text(
                    text = nowPlayingLabel(state.ayahId),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = stringResource(Reciter.fromId(reciterId).nameRes),
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Media controls keep their left-to-right order in Arabic, like the buttons of any player.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = player::previous) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.audio_previous), tint = ink)
                }
                IconButton(onClick = player::togglePause) {
                    Icon(
                        if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (state.playing) R.string.audio_pause else R.string.audio_play),
                        tint = ink
                    )
                }
                IconButton(onClick = player::next) {
                    Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.audio_next), tint = ink)
                }
                IconButton(onClick = player::stop) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.audio_stop), tint = muted)
                }
            }
        }
    }
}

private val REPEATS = listOf(1, 2, 3, 5, 10)
private val SPEEDS = listOf(75, 100, 125, 150)

/** Reciter, how many times each verse plays, speed, and how far a recitation goes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecitationSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val controller = remember(context) { RecitationController.get(context) }
    val prefs = remember(context) { UserPreferencesRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val reciter = Reciter.fromId(prefs.reciterId.collectAsState(initial = "").value)
    val repeat by prefs.recitationRepeat.collectAsState(initial = 1)
    val speed by prefs.recitationSpeedPercent.collectAsState(initial = 100)
    val loop by prefs.recitationLoop.collectAsState(initial = false)
    val toSurahEnd by prefs.recitationToSurahEnd.collectAsState(initial = false)

    /** Saves a setting that needs the run to be rebuilt, and carries on from the verse sounding now. */
    fun change(save: suspend () -> Unit) {
        scope.launch {
            save()
            controller.reapply()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(stringResource(R.string.audio_sheet_title), style = MaterialTheme.typography.titleMedium)

            SheetLabel(stringResource(R.string.audio_reciter))
            Reciter.entries.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.RadioButton) { change { prefs.setReciterId(option.id) } }
                ) {
                    RadioButton(selected = option == reciter, onClick = null, modifier = Modifier.padding(end = 12.dp, top = 12.dp, bottom = 12.dp))
                    Text(stringResource(option.nameRes), style = MaterialTheme.typography.bodyLarge)
                }
            }

            SheetLabel(stringResource(R.string.audio_repeat_each))
            Row(modifier = Modifier.fillMaxWidth()) {
                REPEATS.forEach { times ->
                    PaperChip(
                        selected = times == repeat,
                        onClick = { change { prefs.setRecitationRepeat(times) } },
                        label = { Text(stringResource(R.string.audio_times, times)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            SheetLabel(stringResource(R.string.audio_speed))
            Row(modifier = Modifier.fillMaxWidth()) {
                SPEEDS.forEach { percent ->
                    PaperChip(
                        selected = percent == speed,
                        onClick = {
                            scope.launch {
                                prefs.setRecitationSpeedPercent(percent)
                                controller.player.setSpeed(percent)
                            }
                        },
                        label = { Text("${percent / 100f}×".replace(".0×", "×")) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            ToggleRow(stringResource(R.string.audio_loop), loop) { on -> change { prefs.setRecitationLoop(on) } }
            ToggleRow(stringResource(R.string.audio_to_surah_end), toSurahEnd) { on -> change { prefs.setRecitationToSurahEnd(on) } }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
