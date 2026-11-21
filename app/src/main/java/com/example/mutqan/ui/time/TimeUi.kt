package com.example.mutqan.ui.time

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.domain.HabitSnapshots
import com.example.mutqan.domain.time.TimeRepository
import com.example.mutqan.domain.time.TimeSummary
import com.example.mutqan.ui.components.Folio
import com.example.mutqan.ui.habit.WeekdayNames
import com.example.mutqan.ui.theme.DifficultySmooth
import kotlinx.coroutines.flow.combine

/** The time spent, today, over the last week and in all, read live from the database; null until first read. */
@Composable
fun rememberTimeSummary(): TimeSummary? {
    val context = LocalContext.current
    val today = HabitSnapshots.localDay()
    val flow = remember(context, today) {
        val times = TimeRepository(context)
        combine(times.since(today - 6), times.totals()) { rows, totals -> TimeSummary.of(rows, totals, today) }
    }
    val summary by flow.collectAsState(initial = null)
    return summary
}

/** [millis] in words: "أقل من دقيقة", "25 د", "1 س 20 د". */
@Composable
fun durationText(millis: Long): String {
    val minutes = millis / 60_000L
    return when {
        minutes < 1 -> stringResource(R.string.time_under_minute)
        minutes < 60 -> stringResource(R.string.time_minutes, minutes.toInt())
        minutes % 60 == 0L -> stringResource(R.string.time_hours, (minutes / 60).toInt())
        else -> stringResource(R.string.time_hours_minutes, (minutes / 60).toInt(), (minutes % 60).toInt())
    }
}

/** The colours of the two kinds of time, everywhere they show. */
@Composable
private fun mushafColor(): Color = MaterialTheme.colorScheme.primary

@Composable
private fun practiceColor(): Color = DifficultySmooth

/**
 * The time card: today in the Mushaf and in practice, the last seven days as stacked bars (oldest first, like the week
 * strip on Today), and the week's and all-time totals.
 */
@Composable
fun TimeCard(summary: TimeSummary, modifier: Modifier = Modifier) {
    Folio(modifier = modifier.fillMaxWidth()) {
        if (summary.isEmpty) {
            Text(
                text = stringResource(R.string.time_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Folio
        }
        Text(
            text = stringResource(R.string.time_today),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TodayFigure(stringResource(R.string.time_mushaf), summary.todayMushaf, mushafColor(), Modifier.weight(1f))
            TodayFigure(stringResource(R.string.time_practice), summary.todayPractice, practiceColor(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        WeekBars(summary.week)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.time_week, durationText(summary.weekMushaf), durationText(summary.weekPractice)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.time_total, durationText(summary.totalMushaf), durationText(summary.totalPractice)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TodayFigure(label: String, millis: Long, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = durationText(millis),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Seven columns, Mushaf time under practice time, each as tall as its share of the busiest day. */
@Composable
private fun WeekBars(week: List<Triple<Long, Long, Long>>) {
    val locale = LocalConfiguration.current.locales[0]
    val names = remember(locale) { WeekdayNames(locale) }
    val most = week.maxOfOrNull { it.second + it.third }?.coerceAtLeast(1L) ?: 1L
    val mushaf = mushafColor()
    val practice = practiceColor()
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((day, m, p) in week) {
            val label = stringResource(R.string.time_bar_cd, names.name(day), durationText(m), durationText(p))
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = label },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.weight(1f).width(14.dp).clip(RoundedCornerShape(7.dp)).background(track),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val total = (m + p).toFloat() / most
                    if (total > 0f) {
                        Column(Modifier.fillMaxWidth().fillMaxHeight(total.coerceIn(0.04f, 1f))) {
                            if (p > 0) Box(Modifier.fillMaxWidth().weight(p.toFloat()).background(practice))
                            if (m > 0) Box(Modifier.fillMaxWidth().weight(m.toFloat()).background(mushaf))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = names.letter(day),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 0.dp)
                )
            }
        }
    }
}
