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
                HubRow(stringResource(R.string.progress_title), stringResource(R.string.insights_progress_desc), onOpenProgress)
            }
            HubRow(stringResource(R.string.brain_title), stringResource(R.string.insights_brain_desc), onOpenBrain, compact = true)
            HubRow(stringResource(R.string.insights_log_title), stringResource(R.string.insights_journal_desc), onLogRevision, compact = true)
            HubRow(stringResource(R.string.insights_history_title), stringResource(R.string.insights_history_desc), onOpenHistory, compact = true)
            HubRow(stringResource(R.string.coverage_title), stringResource(R.string.insights_coverage_desc), onOpenCoverage, compact = true)
            HubRow(stringResource(R.string.plan_title), stringResource(R.string.insights_plan_desc), onOpenPlan, compact = true)
        }
    }
}

/** The first thing on the page: how much is memorised, in display type, over a bar of how much is left. */
@Composable
private fun ProgressLead(covered: Int, onClick: () -> Unit) {
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = PaperSpace.Gutter, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SectionLabel(stringResource(R.string.progress_title))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.progress_coverage_value, covered, TOTAL_PAGES),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(22.dp)
                )
            }
            PaperProgress(fraction = covered.toFloat() / TOTAL_PAGES, modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = stringResource(R.string.insights_progress_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Hairline(Modifier.padding(horizontal = PaperSpace.Gutter))
    }
}
