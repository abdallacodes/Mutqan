package com.example.mutqan.ui.habit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.domain.DayMark
import com.example.mutqan.domain.HabitSnapshot
import com.example.mutqan.domain.Streak
import com.example.mutqan.domain.Ward
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.components.AyahText
import com.example.mutqan.ui.components.HeroCard
import com.example.mutqan.ui.components.rememberHaptics
import com.example.mutqan.ui.theme.FolioTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** The revision's share of the ring when there is a revision today; the questions take the rest. */
private const val REVISION_SHARE = 0.7f

/** Degrees left open between the ring's two arcs. */
private const val GAP = 16f

/** Today's ward as a whole, 0 to 100: the revision and the questions, weighted as the ring shows them. */
fun Ward.percent(): Int =
    ((if (hasPortions) REVISION_SHARE * revisionShare + (1 - REVISION_SHARE) * questionsShare else questionsShare) * 100).roundToInt()

/**
 * Today's ward on its card: the ring and the run of days on top, then [planPart] (what the plan asks for now), the day's
 * questions, and [reminderOffer] when there is one. Once the ward is done, the verse of the day takes the plan's place.
 */
@Composable
fun WardCard(
    habit: HabitSnapshot,
    celebrate: Boolean,
    onCelebrated: () -> Unit,
    verseOfDay: Int?,
    onOpenVerse: (Int) -> Unit,
    onOpenQuestions: () -> Unit,
    reminderOffer: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    planPart: @Composable ColumnScope.() -> Unit
) {
    val accent = FolioTheme.colors.heroAccent
    HeroCard(modifier = modifier.fillMaxWidth()) {
        WardTop(habit, celebrate, onCelebrated)
        Rule(accent)
        if (habit.ward.complete) {
            VerseOfDay(verseOfDay, onOpenVerse)
        } else {
            planPart()
        }
        Rule(accent)
        QuestionsLine(habit.ward, onOpenQuestions)
        reminderOffer?.invoke()
    }
}

@Composable
private fun Rule(accent: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .height(1.dp)
            .background(accent.copy(alpha = 0.35f))
    )
}

/** The ring, and beside it what the day is called, the run of days, and the week. */
@Composable
private fun WardTop(habit: HabitSnapshot, celebrate: Boolean, onCelebrated: () -> Unit) {
    val accent = FolioTheme.colors.heroAccent
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        WardRing(habit.ward, celebrate, onCelebrated)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(if (habit.ward.complete) R.string.habit_ward_done else R.string.habit_ward_title),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = accent
            )
            Text(text = streakText(habit.streak), style = MaterialTheme.typography.titleLarge)
            WeekStrip(habit.week)
        }
    }
    // Until today counts, say what makes it count.
    if (!habit.streak.todayKept) {
        Text(
            text = stringResource(R.string.habit_rule),
            style = MaterialTheme.typography.labelMedium,
            color = LocalContentColor.current.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
fun streakText(streak: Streak): String =
    if (streak.days == 0) stringResource(R.string.habit_streak_start)
    else pluralStringResource(R.plurals.habit_streak_days, streak.days, streak.days)

/**
 * Today's ward as a ring: the revision part in gold and the questions in parchment, each filling its own arc (the
 * questions take the whole ring when there is nothing to revise today). The middle shows how far the day is, or a tick
 * once it is done. With [celebrate], the tick pops once (with a tap of the phone) and [onCelebrated] is called.
 */
@Composable
fun WardRing(ward: Ward, celebrate: Boolean, onCelebrated: () -> Unit, modifier: Modifier = Modifier, diameter: Dp = 92.dp) {
    val colors = FolioTheme.colors
    val gold = colors.heroAccent
    val cream = colors.onHero
    val haptics = rememberHaptics()
    val revision = remember { Animatable(0f) }
    val questions = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(ward.revisionShare) { revision.animateTo(ward.revisionShare, tween(700, easing = FastOutSlowInEasing)) }
    LaunchedEffect(ward.questionsShare) { questions.animateTo(ward.questionsShare, tween(700, delayMillis = 150, easing = FastOutSlowInEasing)) }
    LaunchedEffect(celebrate) {
        if (!celebrate) return@LaunchedEffect
        delay(900)
        haptics.confirm()
        pop.animateTo(1.22f, tween(170))
        pop.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 320f))
        onCelebrated()
    }
    val percent = ward.percent()
    val description = stringResource(R.string.habit_ring_cd, percent)
    Box(
        modifier = modifier.size(diameter).semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.105f
            val inset = stroke / 2f
            val box = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            fun arc(start: Float, sweep: Float, color: Color) {
                if (sweep <= 0.5f) return
                drawArc(color, start, sweep, useCenter = false, topLeft = topLeft, size = box, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            if (ward.hasPortions) {
                val open = 360f - 2 * GAP
                val revisionSweep = open * REVISION_SHARE
                val questionsSweep = open - revisionSweep
                val revisionStart = -90f + GAP / 2
                val questionsStart = revisionStart + revisionSweep + GAP
                arc(revisionStart, revisionSweep, gold.copy(alpha = 0.22f))
                arc(questionsStart, questionsSweep, cream.copy(alpha = 0.18f))
                arc(revisionStart, revisionSweep * revision.value, gold)
                arc(questionsStart, questionsSweep * questions.value, cream)
            } else {
                arc(-90f, 360f, cream.copy(alpha = 0.18f))
                arc(-90f, 360f * questions.value, cream)
            }
        }
        if (ward.complete) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = gold,
                modifier = Modifier
                    .size(diameter * 0.46f)
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            )
        } else {
            Text(
                text = stringResource(R.string.habit_percent, percent),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = cream
            )
        }
    }
}

/** The last seven days, oldest first (so today ends the row): each a letter for its weekday over a mark. */
@Composable
fun WeekStrip(week: List<Pair<Long, DayMark>>, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        for ((day, mark) in week) {
            val weekday = remember(day, locale) { LocalDate.ofEpochDay(day).dayOfWeek }
            val letter = remember(weekday, locale) { weekday.getDisplayName(TextStyle.NARROW, locale) }
            val name = remember(weekday, locale) { weekday.getDisplayName(TextStyle.FULL, locale) }
            val state = stringResource(
                when (mark) {
                    DayMark.KEPT -> R.string.habit_day_kept
                    DayMark.FORGIVEN -> R.string.habit_day_forgiven
                    DayMark.REST -> R.string.habit_day_rest
                    DayMark.MISSED -> R.string.habit_day_missed
                    DayMark.OPEN -> R.string.habit_day_open
                }
            )
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$name: $state" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = if (mark == DayMark.OPEN) 1f else 0.7f)
                )
                DayDot(mark)
            }
        }
    }
}

@Composable
private fun DayDot(mark: DayMark) {
    val gold = FolioTheme.colors.heroAccent
    val cream = FolioTheme.colors.onHero
    val side = 14.dp
    when (mark) {
        DayMark.KEPT -> Box(Modifier.size(side).background(gold, CircleShape))
        DayMark.FORGIVEN -> Box(Modifier.size(side).background(gold.copy(alpha = 0.35f), CircleShape))
        DayMark.REST -> Box(Modifier.size(side), contentAlignment = Alignment.Center) {
            Box(Modifier.width(8.dp).height(2.dp).background(gold.copy(alpha = 0.6f), RoundedCornerShape(1.dp)))
        }
        DayMark.MISSED -> Box(Modifier.size(side).border(1.dp, cream.copy(alpha = 0.3f), CircleShape))
        DayMark.OPEN -> Box(Modifier.size(side).border(2.dp, gold, CircleShape))
    }
}

/** The day's questions: how many of the ten are answered, and a way to them; a tick once they are done. */
@Composable
private fun QuestionsLine(ward: Ward, onOpen: () -> Unit) {
    val gold = FolioTheme.colors.heroAccent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onOpen)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.habit_questions), style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (ward.questionsDone) stringResource(R.string.habit_questions_done)
                else stringResource(R.string.habit_questions_count, ward.answered.coerceAtMost(ward.questions), ward.questions),
                style = MaterialTheme.typography.labelMedium,
                color = LocalContentColor.current.copy(alpha = 0.75f)
            )
        }
        if (ward.questionsDone) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = gold, modifier = Modifier.size(24.dp))
        } else {
            Box(
                modifier = Modifier
                    .border(1.dp, gold, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(if (ward.answered > 0) R.string.habit_questions_go_on else R.string.habit_questions_start),
                    style = MaterialTheme.typography.labelLarge,
                    color = gold
                )
            }
        }
    }
}

/** A verse from the user's own hifz, other each day: what the finished ward shows. */
@Composable
private fun VerseOfDay(ayahId: Int?, onOpen: (Int) -> Unit) {
    val context = LocalContext.current
    val gold = FolioTheme.colors.heroAccent
    if (ayahId == null) {
        Text(stringResource(R.string.home_all_done), style = MaterialTheme.typography.titleMedium)
        return
    }
    val index = remember(context) { QuranRepository.getInstance(context).ayahIndex }
    val ref = stringResource(R.string.saved_ref, SurahData.nameOf(index.surahOf(ayahId)), index.ayahOf(ayahId))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) { onOpen(ayahId) },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.habit_verse_label), style = MaterialTheme.typography.labelLarge, color = gold)
        AyahText(ayahId = ayahId, size = 22.sp, color = LocalContentColor.current, firstWords = 24)
        Text(ref, style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.75f))
    }
}

/** On the ward card, once: an offer to be reminded each day, which can be closed for good. */
@Composable
fun ReminderOffer(onTurnOn: () -> Unit, onClose: () -> Unit) {
    val gold = FolioTheme.colors.heroAccent
    Spacer(Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onTurnOn)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = gold, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.reminder_offer), style = MaterialTheme.typography.labelLarge, color = gold)
        }
        IconButton(onClick = onClose) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.reminder_offer_close_cd),
                tint = LocalContentColor.current.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
