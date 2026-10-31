package com.example.mutqan.ui.revision

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.domain.LogEffect
import com.example.mutqan.domain.PlanTier
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.SectionLabel
import com.example.mutqan.ui.theme.DifficultyCritical
import com.example.mutqan.ui.theme.DifficultySmooth
import com.example.mutqan.ui.theme.DifficultyStruggled
import java.util.Locale

/** What logging the revision being entered would do to the strength of its pages, updated as the form changes. */
@Composable
fun EffectCard(effect: LogEffect, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.effect_title))
        when {
            effect.practiceOnly -> Muted(stringResource(R.string.effect_practice))
            effect.tracked == 0 -> Muted(stringResource(R.string.effect_outside, effect.total))
            else -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(
                        Triple(PlanTier.WEAK, R.string.strength_weak, DifficultyCritical),
                        Triple(PlanTier.FAIR, R.string.strength_fair, DifficultyStruggled),
                        Triple(PlanTier.STRONG, R.string.strength_strong, DifficultySmooth)
                    ).forEach { (tier, label, color) ->
                        TierCount(
                            label = stringResource(label),
                            color = color,
                            before = effect.before.getValue(tier),
                            after = effect.after.getValue(tier),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.effect_summary, effect.up, effect.down, effect.unchanged),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Muted(
                    stringResource(R.string.effect_hold, days(effect.holdBefore), days(effect.holdAfter))
                )
                if (effect.tracked < effect.total) Muted(stringResource(R.string.effect_outside, effect.total - effect.tracked))
            }
        }
        Hairline()
    }
}

@Composable
private fun TierCount(label: String, color: Color, before: Int, after: Int, modifier: Modifier = Modifier) {
    val changed = before != after
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.foundation.layout.Box(Modifier.size(8.dp).background(color, CircleShape))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = stringResource(R.string.effect_counts, before, after),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (changed) FontWeight.Bold else FontWeight.Normal,
            color = if (changed) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Days with one decimal below ten, whole above; a dash while there is no estimate yet. */
private fun days(value: Float?): String = when {
    value == null -> "—"
    value < 10f -> String.format(Locale.getDefault(), "%.1f", value)
    else -> String.format(Locale.getDefault(), "%d", value.toInt())
}
