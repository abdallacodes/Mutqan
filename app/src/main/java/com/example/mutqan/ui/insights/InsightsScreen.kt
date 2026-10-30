package com.example.mutqan.ui.insights

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.mutqan.R
import com.example.mutqan.ui.components.HubRow
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.TopBarOverflowMenu

/** Where the user stands and what they have recorded: progress, the memory map, the journal, coverage and the plan. */
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
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())) {
            HubRow(stringResource(R.string.progress_title), stringResource(R.string.insights_progress_desc), onOpenProgress)
            HubRow(stringResource(R.string.brain_title), stringResource(R.string.insights_brain_desc), onOpenBrain)
            HubRow(stringResource(R.string.insights_log_title), stringResource(R.string.insights_journal_desc), onLogRevision)
            HubRow(stringResource(R.string.insights_history_title), stringResource(R.string.insights_history_desc), onOpenHistory)
            HubRow(stringResource(R.string.coverage_title), stringResource(R.string.insights_coverage_desc), onOpenCoverage)
            HubRow(stringResource(R.string.plan_title), stringResource(R.string.insights_plan_desc), onOpenPlan)
        }
    }
}
