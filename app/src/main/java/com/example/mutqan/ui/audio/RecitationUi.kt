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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import android.text.format.Formatter
import com.example.mutqan.domain.audio.RecitationDownloads
import com.example.mutqan.ui.theme.AmiriFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    val prefs = remember(context) { UserPreferencesRepository(context.applicationContext) }
    val reciterId by prefs.reciterId.collectAsState(initial = "")
    val repeat by prefs.recitationRepeat.collectAsState(initial = 1)
    val speed by prefs.recitationSpeedPercent.collectAsState(initial = 100)
    val details = buildString {
        append(stringResource(Reciter.fromId(reciterId).nameRes))
        if (speed != 100) append(" · ").append(speedLabel(speed))
        if (repeat > 1) append(" · ").append(stringResource(R.string.audio_times, repeat))
    }
    Column(modifier = modifier.fillMaxWidth().background(background)) {
    if (state.buffering) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = muted, trackColor = Color.Transparent)
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                text = if (state.yourTurn) stringResource(R.string.audio_your_turn) else details,
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
}

/** "1.25×" for a speed given in percent. */
private fun speedLabel(percent: Int) = "${percent / 100f}×".replace(".0×", "×")

private val REPEATS = listOf(1, 2, 3, 5, 10)
private val SAY_AFTER = listOf(0, 100, 150, 200)
private val SLEEP = listOf(0, 15, 30, 60, RecitationPlayer.SLEEP_AT_SURAH_END)
private val SPEEDS = listOf(75, 100, 125, 150)

/** Reciter, how many times each verse plays, speed, and how far a recitation goes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecitationSheet(onDismiss: () -> Unit, onOpenStorage: (() -> Unit)? = null) {
    val context = LocalContext.current
    val controller = remember(context) { RecitationController.get(context) }
    val prefs = remember(context) { UserPreferencesRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val reciter = Reciter.fromId(prefs.reciterId.collectAsState(initial = "").value)
    val repeat by prefs.recitationRepeat.collectAsState(initial = 1)
    val speed by prefs.recitationSpeedPercent.collectAsState(initial = 100)
    val loop by prefs.recitationLoop.collectAsState(initial = false)
    val toSurahEnd by prefs.recitationToSurahEnd.collectAsState(initial = false)
    val runRepeat by prefs.recitationRunRepeat.collectAsState(initial = 1)
    val sayAfter by prefs.recitationSayAfterPercent.collectAsState(initial = 0)
    val playback by controller.player.state.collectAsState()
    val downloadState by remember(context) { RecitationDownloads.get(context) }.state.collectAsState()
    val kept = downloadState.sizes.values.sum()
    var reciterOpen by remember { mutableStateOf(false) }

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

            if (playback.active) NowPlaying(playback, controller.player)

            SheetLabel(stringResource(R.string.audio_reciter))
            // Only the chosen reciter shows until the list is opened.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { reciterOpen = !reciterOpen }
                    .padding(vertical = 10.dp)
            ) {
                Text(stringResource(reciter.nameRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(
                    if (reciterOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (reciterOpen) {
                Reciter.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) {
                                reciterOpen = false
                                change { prefs.setReciterId(option.id) }
                            }
                    ) {
                        RadioButton(selected = option == reciter, onClick = null, modifier = Modifier.padding(end = 12.dp, top = 12.dp, bottom = 12.dp))
                        Text(stringResource(option.nameRes), style = MaterialTheme.typography.bodyLarge)
                    }
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

            SheetLabel(stringResource(R.string.audio_passage_repeat))
            Row(modifier = Modifier.fillMaxWidth()) {
                REPEATS.forEach { times ->
                    PaperChip(
                        selected = times == runRepeat,
                        onClick = { change { prefs.setRecitationRunRepeat(times) } },
                        label = { Text(stringResource(R.string.audio_times, times)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            SheetLabel(stringResource(R.string.audio_say_after))
            Text(
                text = stringResource(R.string.audio_say_after_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                SAY_AFTER.forEach { percent ->
                    PaperChip(
                        selected = percent == sayAfter,
                        onClick = { change { prefs.setRecitationSayAfterPercent(percent) } },
                        label = { Text(if (percent == 0) stringResource(R.string.audio_off) else speedLabel(percent)) },
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
                        label = { Text(speedLabel(percent)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            ToggleRow(stringResource(R.string.audio_loop), loop) { on -> change { prefs.setRecitationLoop(on) } }
            ToggleRow(stringResource(R.string.audio_to_surah_end), toSurahEnd) { on -> change { prefs.setRecitationToSurahEnd(on) } }

            if (playback.active) {
                SheetLabel(stringResource(R.string.audio_sleep))
                Row(modifier = Modifier.fillMaxWidth()) {
                    SLEEP.forEach { option ->
                        PaperChip(
                            selected = option == playback.sleep,
                            onClick = { controller.player.setSleep(option) },
                            label = {
                                Text(
                                    when {
                                        option == 0 -> stringResource(R.string.audio_off)
                                        option > 0 -> stringResource(R.string.audio_sleep_minutes, option)
                                        else -> stringResource(R.string.audio_sleep_surah)
                                    },
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.weight(if (option < 0) 2f else 1f)
                        )
                    }
                }
            }

            if (kept > 0 && onOpenStorage != null) {
                SheetLabel(stringResource(R.string.audio_downloads))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpenStorage).padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.audio_downloads_size, Formatter.formatShortFileSize(context, kept)),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.audio_storage_manage),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/** The verse sounding: its text, where in it the recitation is (drag to move), and the transport controls. */
@Composable
private fun NowPlaying(state: RecitationPlayer.State, player: RecitationPlayer) {
    val context = LocalContext.current
    val quran = remember(context) { QuranRepository.getInstance(context) }
    val text by produceState("", state.ayahId) {
        val id = state.ayahId
        value = if (id == null) "" else withContext(Dispatchers.IO) { quran.ayahById(id)?.text.orEmpty() }
    }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(state.ayahId) {
        while (true) {
            position = player.positionMs()
            duration = player.durationMs()
            delay(250)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = nowPlayingLabel(state.ayahId),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = AmiriFontFamily, fontSize = 24.sp, lineHeight = 44.sp),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        )
        if (state.yourTurn) {
            Text(
                text = stringResource(R.string.audio_your_turn),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Slider(
                value = (dragging ?: position.toFloat()).coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { player.seekTo(it.toLong()) }
                    dragging = null
                },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                enabled = duration > 0
            )
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(clock(dragging?.toLong() ?: position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text(clock(duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = player::previous) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.audio_previous))
                }
                IconButton(onClick = player::togglePause) {
                    Icon(
                        if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (state.playing) R.string.audio_pause else R.string.audio_play),
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = player::next) {
                    Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.audio_next))
                }
            }
        }
    }
}

private fun clock(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
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
