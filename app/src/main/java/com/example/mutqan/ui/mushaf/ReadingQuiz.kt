package com.example.mutqan.ui.mushaf

import android.content.pm.ApplicationInfo
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.mutqan.R
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.domain.time.TimeKind
import com.example.mutqan.domain.time.UserActivity
import com.example.mutqan.ui.components.FolioGroup
import com.example.mutqan.ui.components.HeroCard
import com.example.mutqan.ui.components.IconMedallion
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperOutlinedButton
import com.example.mutqan.ui.components.PaperDialog
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.rememberHaptics
import com.example.mutqan.ui.theme.FolioTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The choices for how often a quiz is offered while reading, in minutes; 0 is never. */
val READING_QUIZ_CHOICES = listOf(0, 10, 15, 20, 30)

/** "الصفحة 56" or "الصفحات 50–56" (or a list, when the pages read were far apart). */
@Composable
fun readingPagesText(pages: Set<Int>): String {
    val sorted = pages.sorted()
    return when {
        sorted.size == 1 -> stringResource(R.string.reading_quiz_page, sorted.first())
        sorted.last() - sorted.first() + 1 == sorted.size -> stringResource(R.string.reading_quiz_pages, sorted.first(), sorted.last())
        else -> stringResource(R.string.reading_quiz_page_list, sorted.joinToString("، "))
    }
}

@Composable
private fun everyText(minutes: Int): String =
    if (minutes <= 0) stringResource(R.string.reading_quiz_off)
    else pluralStringResource(R.plurals.reading_quiz_every, minutes, minutes)

/** The setting in Settings: how often a quiz is offered during a long reading in the Mushaf. */
@Composable
fun ReadingQuizSettingsRow() {
    val context = LocalContext.current
    val prefs = remember(context) { UserPreferencesRepository(context.applicationContext) }
    val minutes by prefs.readingQuizMinutes.collectAsState(initial = null)
    val current = minutes ?: return
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    // A test build also offers one minute, to see the offer without reading for ten.
    val choices = remember(context) {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) listOf(0, 1) + READING_QUIZ_CHOICES.drop(1) else READING_QUIZ_CHOICES
    }

    FolioGroup(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { picking = true }
                .heightIn(min = 64.dp)
                .padding(vertical = 10.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconMedallion(Icons.Outlined.Quiz, size = 36.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.reading_quiz_setting), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.reading_quiz_setting_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(everyText(current), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    }

    if (picking) {
        PaperDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.reading_quiz_setting)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    Text(stringResource(R.string.reading_quiz_setting_hint), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.padding(top = 8.dp))
                    choices.forEach { choice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = choice == current, role = Role.RadioButton) {
                                    picking = false
                                    scope.launch { prefs.setReadingQuizMinutes(choice) }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = choice == current, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(everyText(choice), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { PaperLink(text = stringResource(R.string.session_close), onClick = { picking = false }, soft = true) }
        )
    }
}

/**
 * The reading counted toward the next quiz offer: the time the Mushaf is in front and read (touched within the
 * Mushaf's idle limit, or the recitation going on), and the pages seen meanwhile. Every [everyMinutes] of reading the
 * offer comes up; "later" waits as long again, and a quiz taken starts the count and the pages afresh.
 */
@Stable
class ReadingQuizOffer {
    var visible by mutableStateOf(false)
        private set
    var pages by mutableStateOf<Set<Int>>(emptySet())
        private set

    /** How long was read when the offer came up, in minutes, for its title. */
    var readMinutes by mutableIntStateOf(0)
        private set

    internal var counted by mutableLongStateOf(0L)
    private var totalRead = 0L

    internal fun add(step: Long, everyMs: Long, page: Set<Int>) {
        if (page.isNotEmpty()) pages = pages + page
        counted += step
        totalRead += step
        if (!visible && everyMs > 0 && counted >= everyMs) {
            readMinutes = (totalRead / 60_000L).toInt().coerceAtLeast(1)
            visible = true
        }
    }

    fun later() {
        visible = false
        counted = 0
    }

    /** The pages to ask about; the count and the pages start again. */
    fun take(): Set<Int> {
        val asked = pages
        visible = false
        counted = 0
        totalRead = 0
        pages = emptySet()
        return asked
    }
}

/**
 * Keeps [offer] counting while the Mushaf is in front: [pagesInView] are the pages shown now, [paused] stops the count
 * (the cover is on, so the reader is testing already), [keepGoing] counts with no touch (the recitation).
 */
@Composable
fun rememberReadingQuizOffer(
    pagesInView: Set<Int>,
    paused: Boolean,
    keepGoing: () -> Boolean
): ReadingQuizOffer {
    val context = LocalContext.current
    val prefs = remember(context) { UserPreferencesRepository(context.applicationContext) }
    val minutes by prefs.readingQuizMinutes.collectAsState(initial = 0)
    val offer = remember { ReadingQuizOffer() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val inView by rememberUpdatedState(pagesInView)
    val stop by rememberUpdatedState(paused)
    val keep by rememberUpdatedState(keepGoing)
    val haptics = rememberHaptics()
    LaunchedEffect(minutes, lifecycle) {
        if (minutes <= 0) return@LaunchedEffect
        val everyMs = minutes * 60_000L
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = SystemClock.elapsedRealtime()
            while (true) {
                delay(TICK_MS)
                val now = SystemClock.elapsedRealtime()
                val step = (now - last).coerceIn(0L, TICK_MS * 2)
                last = now
                if (stop) continue
                if (UserActivity.activeWithin(TimeKind.MUSHAF.idleMs, now) || keep()) {
                    val was = offer.visible
                    offer.add(step, everyMs, inView)
                    if (!was && offer.visible) haptics.tick()
                }
            }
        }
    }
    return offer
}

private const val TICK_MS = 5_000L

/** The offer itself: a card over the bottom of the page, with the pages read and a button to start. */
@Composable
fun ReadingQuizCard(offer: ReadingQuizOffer, onStart: (Set<Int>) -> Unit, modifier: Modifier = Modifier, onBreak: ((Set<Int>) -> Unit)? = null) {
    AnimatedVisibility(
        visible = offer.visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = modifier
    ) {
        val accent = FolioTheme.colors.heroAccent
        HeroCard(modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconMedallion(Icons.Outlined.Quiz, size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = pluralStringResource(R.plurals.reading_quiz_read, offer.readMinutes, offer.readMinutes),
                        style = MaterialTheme.typography.labelLarge,
                        color = accent
                    )
                    Text(
                        text = stringResource(R.string.reading_quiz_offer),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (offer.pages.isNotEmpty()) {
                        Text(
                            text = readingPagesText(offer.pages),
                            style = MaterialTheme.typography.bodySmall,
                            color = FolioTheme.colors.onHero.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            Spacer(Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PaperButton(
                    text = stringResource(R.string.reading_quiz_start),
                    onClick = { onStart(offer.take()) },
                    modifier = Modifier.weight(1f)
                )
                // Or a break that stays with what was read: «فوانيس», calm, on these pages.
                if (onBreak != null) {
                    PaperOutlinedButton(
                        text = stringResource(R.string.reading_quiz_game),
                        onClick = { onBreak(offer.take()) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PaperLink(text = stringResource(R.string.reading_quiz_later), onClick = offer::later, soft = true)
            }
        }
    }
}
