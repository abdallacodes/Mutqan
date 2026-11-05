package com.example.mutqan.ui.revision

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.domain.MistakeTag
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperChip
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.localizedLabel
import com.example.mutqan.ui.theme.DifficultyCritical
import com.example.mutqan.ui.theme.DifficultySmooth
import com.example.mutqan.ui.theme.DifficultyStruggled
import androidx.compose.ui.draw.clip
import com.example.mutqan.ui.theme.FolioTheme

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
 *
 * With a [preview], each choice says what it would do to the strength of the pages being logged, and the chosen one
 * is spelled out below the list. [onOpenHowItWorks] adds a link to the explanation of how pages move between levels.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutcomePicker(
    outcome: RecallOutcome,
    tags: Set<MistakeTag>,
    onOutcomeChange: (RecallOutcome) -> Unit,
    onTagsChange: (Set<MistakeTag>) -> Unit,
    modifier: Modifier = Modifier,
    preview: OutcomePreview? = null,
    onOpenHowItWorks: (() -> Unit)? = null
) {
    val effects = rememberOutcomeEffects(preview)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.label_recall_outcome),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        RecallOutcome.entries.forEach { option ->
            OutcomeRow(
                label = option.localizedLabel(),
                selected = option == outcome,
                caption = effects?.get(option)?.caption(),
                onClick = { onOutcomeChange(option) }
            )
        }
        if (preview != null && preview.pages == null) {
            Text(
                text = stringResource(R.string.effect_enter_pages),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (!outcome.isRecallAttempt) {
            Text(
                text = stringResource(R.string.outcome_practice_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Text(
                text = stringResource(R.string.label_mistake_tags),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MistakeTag.entries.forEach { tag ->
                    PaperChip(
                        selected = tag in tags,
                        onClick = { onTagsChange(if (tag in tags) tags - tag else tags + tag) },
                        label = { Text(tag.localizedLabel()) }
                    )
                }
            }
        }
        effects?.get(outcome)?.let { EffectCard(it, modifier = Modifier.padding(top = 12.dp)) }
        if (onOpenHowItWorks != null) PaperLink(stringResource(R.string.how_link), onClick = onOpenHowItWorks, soft = true)
    }
}

/** One choice on its own card: a round mark, its name, and under it what it would do to the strength of the pages. */
@Composable
private fun OutcomeRow(label: String, selected: Boolean, caption: Pair<String, Color>?, onClick: () -> Unit) {
    val colors = FolioTheme.colors
    val shape = FolioTheme.shapes.control
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(if (selected) colors.tonal.copy(alpha = 0.6f) else colors.folio, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) ring else MaterialTheme.colorScheme.outlineVariant, shape)
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(20.dp).border(if (selected) 2.dp else 1.dp, ring, CircleShape)
        ) {
            if (selected) Box(Modifier.size(10.dp).background(ring, CircleShape))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (caption != null) {
                Text(text = caption.first, style = MaterialTheme.typography.labelMedium, color = caption.second)
            }
        }
    }
}
