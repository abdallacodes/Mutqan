package com.example.mutqan.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.domain.PassageState
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.OrnamentDivider
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.SectionLabel
import com.example.mutqan.ui.components.localizedLabel
import com.example.mutqan.ui.theme.DifficultyCritical
import com.example.mutqan.ui.theme.DifficultySmooth
import com.example.mutqan.ui.theme.DifficultyStruggled

/**
 * One page that says where the user stands, from what they recorded: how much is memorised,
 * what their recall shows, how regularly they revised, and where a look would help.
 * Wording is encouraging and never counts missed days against the user.
 */
@Composable
fun ProgressScreen(onBack: () -> Unit, onOpenCoverage: () -> Unit, onOpenMushaf: (Int) -> Unit) {
    val context = LocalContext.current
    val viewModel: ProgressViewModel = viewModel(factory = ProgressViewModelFactory(context))
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { PaperHeader(title = stringResource(R.string.progress_title), subtitle = stringResource(R.string.progress_subtitle), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (!state.isLoaded) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = PaperSpace.Gutter),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // ── Coverage ──
            item {
                SectionLabel(stringResource(R.string.progress_section_coverage))
                if (state.coveredPages == 0) {
                    Text(
                        text = stringResource(R.string.progress_coverage_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(R.string.progress_coverage_value, state.coveredPages, 604),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                PaperLink(stringResource(R.string.progress_open_coverage), onClick = onOpenCoverage)
            }

            if (state.coveredPages > 0) {
                // ── What recall shows ──
                item {
                    Spacer(Modifier.height(PaperSpace.Section - 6.dp))
                    OrnamentDivider()
                    Spacer(Modifier.height(14.dp))
                    SectionLabel(stringResource(R.string.progress_section_evidence))
                    Spacer(Modifier.height(8.dp))
                    EvidenceBar(state.stateCounts, state.coveredPages)
                }
                items(PassageState.entries.reversed().filter { (state.stateCounts[it] ?: 0) > 0 }, key = { it }) { s ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.size(10.dp).background(s.tone(), CircleShape))
                        Text(
                            text = s.localizedLabel(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.progress_pages, state.stateCounts.getValue(s)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Hairline()
                }

                // ── Consistency ──
                item {
                    Spacer(Modifier.height(PaperSpace.Section - 6.dp))
                    OrnamentDivider()
                    Spacer(Modifier.height(14.dp))
                    SectionLabel(stringResource(R.string.progress_section_consistency))
                    Text(
                        text = stringResource(R.string.progress_days, state.daysRevised, WINDOW_DAYS),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    DaysGrid(state.revisedDays)
                }

                // ── Worth a look ──
                item {
                    Spacer(Modifier.height(PaperSpace.Section - 6.dp))
                    OrnamentDivider()
                    Spacer(Modifier.height(14.dp))
                    SectionLabel(stringResource(R.string.progress_section_attention))
                    if (state.attention.isEmpty()) {
                        Text(
                            text = stringResource(R.string.progress_none_attention),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(state.attention, key = { "juz-${it.juz}" }) { a ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.progress_juz_attention, a.juz, a.pages),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        PaperLink(stringResource(R.string.home_btn_open), onClick = { onOpenMushaf(a.firstPage) })
                    }
                    Hairline()
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PassageState.tone(): Color = when (this) {
    PassageState.NOT_ASSESSED -> MaterialTheme.colorScheme.outline
    PassageState.NEEDS_ATTENTION -> DifficultyCritical
    PassageState.DEVELOPING -> DifficultyStruggled
    PassageState.RELIABLE -> DifficultySmooth
    PassageState.STABLE_OVER_TIME -> MaterialTheme.colorScheme.primary
}

/** A thin bar split by share of memorised pages in each state. */
@Composable
private fun EvidenceBar(counts: Map<PassageState, Int>, total: Int) {
    Row(modifier = Modifier.fillMaxWidth().height(8.dp)) {
        PassageState.entries.forEach { s ->
            val n = counts[s] ?: 0
            if (n > 0) Box(Modifier.weight(n.toFloat() / total).fillMaxSize().background(s.tone()))
        }
    }
}

/** The last days as dots, oldest first, seven to a row: filled when something was revised. */
@Composable
private fun DaysGrid(days: List<Boolean>) {
    val filled = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { revised -> Box(Modifier.size(14.dp).background(if (revised) filled else empty, CircleShape)) }
            }
        }
    }
}
