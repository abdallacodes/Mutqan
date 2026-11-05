package com.example.mutqan.ui.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.HubRow
import com.example.mutqan.ui.components.LocalDockInset
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperProgress
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.SectionLabel
import com.example.mutqan.ui.components.TopBarOverflowMenu
import com.example.mutqan.ui.navigation.OnTabReselected
import com.example.mutqan.ui.navigation.Routes
import com.example.mutqan.ui.progress.ProgressViewModel
import com.example.mutqan.ui.progress.ProgressViewModelFactory
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.ui.text.font.FontWeight
import com.example.mutqan.ui.components.HeroCard
import com.example.mutqan.ui.components.PageStrip
import com.example.mutqan.ui.theme.FolioTheme

private const val TOTAL_PAGES = 604

/**
 * Where the user stands and what they have recorded. Progress leads, with the number itself on it;
 * the memory map, the journal, coverage and the plan follow as quieter rows.
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
                ProgressLead(covered = state.coveredPages, onClick = onOpenProgress)
            } else {
                HubRow(stringResource(R.string.progress_title), stringResource(R.string.insights_progress_desc), onOpenProgress, icon = Icons.Rounded.Insights)
            }
            HubRow(stringResource(R.string.brain_title), stringResource(R.string.insights_brain_desc), onOpenBrain, compact = true, icon = Icons.Rounded.Psychology)
            HubRow(stringResource(R.string.insights_log_title), stringResource(R.string.insights_journal_desc), onLogRevision, compact = true, icon = Icons.Rounded.EditNote)
            HubRow(stringResource(R.string.insights_history_title), stringResource(R.string.insights_history_desc), onOpenHistory, compact = true, icon = Icons.Rounded.History)
            HubRow(stringResource(R.string.coverage_title), stringResource(R.string.insights_coverage_desc), onOpenCoverage, compact = true, icon = Icons.Rounded.DoneAll)
            HubRow(stringResource(R.string.plan_title), stringResource(R.string.insights_plan_desc), onOpenPlan, compact = true, icon = Icons.Rounded.CalendarMonth)
        }
    }
}

/** The first thing on the page: how much is memorised, in display type on the deep card, over a bar of how much is left. */
@Composable
private fun ProgressLead(covered: Int, onClick: () -> Unit) {
    val accent = FolioTheme.colors.heroAccent
    HeroCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 8.dp),
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
            modifier = Modifier.padding(vertical = 10.dp)
        )
        Text(
            text = stringResource(R.string.insights_progress_desc),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
