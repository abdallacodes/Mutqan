package com.example.mutqan.ui.revision

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.domain.MistakeTag
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.ui.components.localizedLabel
import com.example.mutqan.ui.theme.DifficultyCritical
import com.example.mutqan.ui.theme.DifficultySmooth
import com.example.mutqan.ui.theme.DifficultyStruggled

/** The accent colour for an outcome: green for recall, amber for help, red for none, grey for practice. */
@Composable
fun RecallOutcome.color(): Color = when (this) {
    RecallOutcome.INDEPENDENT, RecallOutcome.SELF_CORRECTED -> DifficultySmooth
    RecallOutcome.HINTED -> DifficultyStruggled
    RecallOutcome.UNABLE -> DifficultyCritical
    RecallOutcome.PRACTICE_ONLY -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Chooses what a session demonstrated and, for recall attempts, which mistakes occurred.
 * Replaces the old quality slider: the user states what happened and the app does the rest.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutcomePicker(
    outcome: RecallOutcome,
    tags: Set<MistakeTag>,
    onOutcomeChange: (RecallOutcome) -> Unit,
    onTagsChange: (Set<MistakeTag>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.label_recall_outcome),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecallOutcome.entries.forEach { option ->
                FilterChip(
                    selected = option == outcome,
                    onClick = { onOutcomeChange(option) },
                    label = { Text(option.localizedLabel()) }
                )
            }
        }

        if (!outcome.isRecallAttempt) {
            Text(
                text = stringResource(R.string.outcome_practice_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = stringResource(R.string.label_mistake_tags),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MistakeTag.entries.forEach { tag ->
                    FilterChip(
                        selected = tag in tags,
                        onClick = { onTagsChange(if (tag in tags) tags - tag else tags + tag) },
                        label = { Text(tag.localizedLabel()) }
                    )
                }
            }
        }
    }
}
