package com.example.mutqan.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.domain.PassageState
import com.example.mutqan.ui.components.HeroCard
import com.example.mutqan.ui.components.IndexRow
import com.example.mutqan.ui.components.LocalDockInset
import com.example.mutqan.ui.components.PageStrip
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.PrimaryButton
import com.example.mutqan.ui.components.SectionHead
import com.example.mutqan.ui.components.TopBarOverflowMenu
import com.example.mutqan.ui.components.localizedLabel
import com.example.mutqan.ui.navigation.OnTabReselected
import com.example.mutqan.ui.navigation.Routes
import com.example.mutqan.ui.progress.JuzMemory
import com.example.mutqan.ui.progress.ProgressViewModel
import com.example.mutqan.ui.progress.ProgressViewModelFactory
import com.example.mutqan.ui.progress.tone
import com.example.mutqan.ui.theme.FolioTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val TOTAL_PAGES = 604
private const val JUZ_COUNT = 30
private const val WEEK = 7

/**
 * Where the user stands and what they have recorded. The progress hero leads and the one action of the tab, logging a
 * revision, follows it. Then two parts that draw their own data, the memory map (every juz, by recall state) and the
 * week, and last the quiet ways to organise what is memorised.
 */
@Composable
fun InsightsScreen(
    onOpenProgress: () -> Unit,
    onOpenBrain: () -> Unit,
    onLogRevision: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCoverage: () -> Unit,
    onOpenPlan: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val scroll = rememberScrollState()
    OnTabReselected(Routes.INSIGHTS) { scroll.animateScrollTo(0) }

    val progress: ProgressViewModel = viewModel(factory = ProgressViewModelFactory(LocalContext.current))
    val state by progress.uiState.collectAsState()

    Scaffold(
        topBar = {
            PaperHeader(
                title = stringResource(R.string.insights_title),
                subtitle = stringResource(R.string.insights_subtitle),
                actions = { TopBarOverflowMenu(onHelpClick = null, onSettingsClick = onSettingsClick) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(scroll).padding(bottom = LocalDockInset.current)) {
            if (state.coveredPages > 0) {
                ProgressHero(
                    covered = state.coveredPages,
                    untested = state.stateCounts[PassageState.NOT_ASSESSED] ?: 0,
                    onClick = onOpenProgress
                )
            } else if (state.isLoaded) {
                // Nothing is marked as memorised yet: the one useful thing to do is to mark it.
                NothingYet(onOpenCoverage)
            }

            PrimaryButton(
                text = stringResource(R.string.insights_log_title),
                onClick = onLogRevision,
                icon = Icons.Rounded.Add,
                pill = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter).padding(top = 14.dp)
            )

            if (state.coveredPages > 0) {
                SectionHead(
                    title = stringResource(R.string.progress_map_title),
                    actionLabel = stringResource(R.string.progress_map_open),
                    onAction = onOpenBrain
                )
                MemoryMap(state.juzMap, state.stateCounts, onClick = onOpenBrain)
            }

            SectionHead(
                title = stringResource(R.string.progress_week_title),
                actionLabel = stringResource(R.string.insights_history_title),
                onAction = onOpenHistory
            )
            WeekBars(revisedDays = state.revisedDays, daysRevised = state.daysRevised)

            SectionHead(title = stringResource(R.string.progress_organise_title))
            IndexRow(
                title = stringResource(R.string.plan_title),
                description = stringResource(R.string.insights_plan_desc),
                onClick = onOpenPlan
            )
            IndexRow(
                title = stringResource(R.string.coverage_title),
                description = stringResource(R.string.insights_coverage_desc),
                value = if (state.coveredPages > 0) pluralStringResource(R.plurals.count_pages, state.coveredPages, state.coveredPages) else null,
                onClick = onOpenCoverage
            )
        }
    }
}

/** The first thing on the page: how much is memorised, in display type on the deep card, over a bar of how much is left. */
@Composable
private fun ProgressHero(covered: Int, untested: Int, onClick: () -> Unit) {
    val accent = FolioTheme.colors.heroAccent
    HeroCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter).padding(top = 12.dp),
        onClick = onClick
    ) {
        Text(
            text = stringResource(R.string.progress_title),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = accent
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.progress_coverage_value, covered, TOTAL_PAGES),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(24.dp)
            )
        }
        PageStrip(
            fraction = covered.toFloat() / TOTAL_PAGES,
            track = accent.copy(alpha = 0.25f),
            color = accent,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
        )
        Text(
            text = if (untested == 0) stringResource(R.string.progress_hero_all_tested)
            else stringResource(R.string.progress_hero_tested, covered - untested, untested),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** Before anything is marked as memorised: a sentence and the way to mark it. */
@Composable
private fun NothingYet(onOpenCoverage: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter).padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.home_no_coverage_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.progress_coverage_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PrimaryButton(
            text = stringResource(R.string.home_no_coverage_title),
            onClick = onOpenCoverage,
            pill = true,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}

/** The states in the order a column is stacked, bottom first: what has been tested leads, so even a few reviewed pages show. */
private val STACK = listOf(
    PassageState.STABLE_OVER_TIME,
    PassageState.RELIABLE,
    PassageState.DEVELOPING,
    PassageState.NEEDS_ATTENTION,
    PassageState.NOT_ASSESSED
)

/**
 * The thirty juz as thirty columns, in the direction of reading. A column rises as far as the juz is memorised, and is
 * split by recall state: every state has its own band, so a juz with two reviewed pages among twenty shows those two in
 * their colour over the pale band of the rest. Under it, the states that occur, with their page counts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryMap(juz: List<JuzMemory>, counts: Map<PassageState, Int>, onClick: () -> Unit) {
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    // Memorised and not yet tested is a mid grey: neutral, so it is never taken for one of the coloured states (the
    // accent is gold on Paper, which sat too close to amber), and plainly apart from the faint track of what is not memorised.
    val untested = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f)
    val tones = PassageState.entries.associateWith { if (it == PassageState.NOT_ASSESSED) untested else it.tone() }
    val description = stringResource(R.string.progress_map_desc)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .semantics { contentDescription = description }
                .clickable(role = Role.Button, onClick = onClick)
        ) {
            val gap = 2.dp.toPx()
            val cell = (size.width - gap * (JUZ_COUNT - 1)) / JUZ_COUNT
            val rtl = layoutDirection == LayoutDirection.Rtl
            val corner = CornerRadius(3.dp.toPx())
            val stub = 3.dp.toPx()
            juz.forEachIndexed { i, j ->
                val x = i * (cell + gap)
                val left = if (rtl) size.width - x - cell else x
                drawRoundRect(track, Offset(left, 0f), Size(cell, size.height), corner)
                if (j.covered > 0 && j.total > 0) {
                    val column = Path().apply { addRoundRect(RoundRect(left, 0f, left + cell, size.height, corner)) }
                    clipPath(column) {
                        var bottom = size.height
                        for (state in STACK) {
                            val n = j.byState[state] ?: 0
                            if (n == 0) continue
                            // At least a stub, so one page in a state still shows.
                            val band = maxOf(size.height * n / j.total, stub)
                            val top = maxOf(bottom - band, 0f)
                            drawRect(tones.getValue(state), Offset(left, top), Size(cell, bottom - top))
                            bottom = top
                            if (bottom <= 0f) break
                        }
                    }
                }
            }
        }
        // 1, 10, 20 and 30 under their columns; Alignment flips for right-to-left.
        Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics { }) {
            listOf(1, 10, 20, 30).forEach { n ->
                Text(
                    text = "$n",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(BiasAlignment(-1f + 2f * (n - 1) / (JUZ_COUNT - 1), 0f))
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(PassageState.NEEDS_ATTENTION, PassageState.DEVELOPING, PassageState.NOT_ASSESSED, PassageState.RELIABLE, PassageState.STABLE_OVER_TIME)
                .filter { (counts[it] ?: 0) > 0 }
                .forEach { s ->
                    val n = counts.getValue(s)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(10.dp).background(tones.getValue(s), CircleShape))
                        Text(
                            text = "${s.localizedLabel()} · ${pluralStringResource(R.plurals.count_pages, n, n)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
        }
    }
}

/** The last seven days as seven bars, oldest first (so, in Arabic, from the right): tall when something was revised that day. */
@Composable
private fun WeekBars(revisedDays: List<Boolean>, daysRevised: Int) {
    val week = revisedDays.takeLast(WEEK)
    val letters = remember(week.size) { weekdayLetters(week.size) }
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outlineVariant
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter)) {
        Row(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            week.forEachIndexed { i, revised ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
                    Box(modifier = Modifier.height(58.dp), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier
                                .width(24.dp)
                                .height(if (revised) 46.dp else 8.dp)
                                .background(if (revised) on else off, RoundedCornerShape(12.dp))
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = letters.getOrElse(i) { "" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (revisedDays.isNotEmpty()) {
            Text(
                text = pluralStringResource(R.plurals.progress_days, daysRevised, daysRevised, revisedDays.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

/** The one-letter weekday names of the last [days] days ending today, oldest first, in the language of the app. */
private fun weekdayLetters(days: Int): List<String> {
    val format = SimpleDateFormat("EEEEE", Locale.getDefault())
    return (days - 1 downTo 0).map { back ->
        format.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -back) }.time)
    }
}
