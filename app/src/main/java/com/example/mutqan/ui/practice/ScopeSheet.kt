package com.example.mutqan.ui.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mutqan.R
import com.example.mutqan.domain.PlanTier
import com.example.mutqan.domain.PracticeScope
import com.example.mutqan.domain.RecencyUnit
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperChip
import com.example.mutqan.ui.components.PaperChoice
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.PaperStepper
import com.example.mutqan.ui.components.SectionLabel
import com.example.mutqan.ui.theme.DifficultyCritical
import com.example.mutqan.ui.theme.DifficultySmooth
import com.example.mutqan.ui.theme.DifficultyStruggled
import com.example.mutqan.ui.components.PaperDialog

// ── Words for a choice ──────────────────────────────────────────────────────

private fun spanPlural(unit: RecencyUnit): Int = when (unit) {
    RecencyUnit.PAGES -> R.plurals.scope_span_pages
    RecencyUnit.DAYS -> R.plurals.scope_span_days
    RecencyUnit.WEEKS -> R.plurals.scope_span_weeks
    RecencyUnit.MONTHS -> R.plurals.scope_span_months
    RecencyUnit.YEARS -> R.plurals.scope_span_years
}

@Composable
private fun span(unit: RecencyUnit, amount: Int): String = pluralStringResource(spanPlural(unit), amount, amount)

private fun tierLabel(tier: PlanTier): Int = when (tier) {
    PlanTier.WEAK -> R.string.practice_scope_weak
    PlanTier.FAIR -> R.string.practice_scope_fair
    PlanTier.STRONG -> R.string.practice_scope_strong
}

private fun tierColor(tier: PlanTier): Color = when (tier) {
    PlanTier.WEAK -> DifficultyCritical
    PlanTier.FAIR -> DifficultyStruggled
    PlanTier.STRONG -> DifficultySmooth
}

/** A choice in a line of words: "Reviewed in the last 2 weeks · Weak, Fair". */
@Composable
fun scopeSummary(scope: PracticeScope): String {
    val reach = when (scope.reach) {
        PracticeScope.Reach.ALL -> stringResource(R.string.scope_reach_all)
        PracticeScope.Reach.COUNT -> stringResource(R.string.scope_reach_count, span(RecencyUnit.PAGES, scope.recentAmount))
        PracticeScope.Reach.TIME -> stringResource(R.string.scope_reach_time, span(scope.recentUnit, scope.recentAmount))
    }
    if (scope.tiers.isEmpty()) return reach
    val separator = stringResource(R.string.scope_list_separator)
    val levels = listOf(PlanTier.WEAK, PlanTier.FAIR, PlanTier.STRONG)
        .filter { it in scope.tiers }
        .map { stringResource(tierLabel(it)) }
        .joinToString(separator)
    return "$reach · $levels"
}

// ── On the Practice list ────────────────────────────────────────────────────

/** The choice of pages as one line on the Practice list: what it is in words, how many pages, and a way in to change it. */
@Composable
fun ScopeRow(scope: PracticeScope, preview: PracticeScope.Preview?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = PaperSpace.Gutter, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionLabel(stringResource(R.string.scope_row_title))
                Text(
                    text = scopeSummary(scope),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            if (preview != null) {
                Text(
                    text = pluralStringResource(R.plurals.scope_span_pages, preview.total, preview.total),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (preview.total == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                )
            }
            Icon(
                Icons.Default.Tune,
                contentDescription = stringResource(R.string.scope_change),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Hairline()
    }
}

/** Under the title of an exercise: which pages its questions come from, and a way back to change that. */
@Composable
fun ScopeLine(scope: PracticeScope, onChange: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.scope_quiz_line, scopeSummary(scope)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        PaperLink(stringResource(R.string.scope_change), onClick = onChange)
    }
}

// ── The sheet ───────────────────────────────────────────────────────────────

/**
 * Where the choice is made: which pages (all, the latest ones reviewed, or those reviewed within a stretch of time),
 * then which levels of those. It shows how many pages the choice gives while it is being made.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScopeSheet(
    scope: PracticeScope,
    preview: PracticeScope.Preview?,
    onChange: (PracticeScope) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PaperSpace.Gutter)
        ) {
            Text(
                text = stringResource(R.string.scope_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            SectionLabel(stringResource(R.string.scope_pages_label), modifier = Modifier.padding(top = 16.dp))
            Hairline(Modifier.padding(top = 6.dp))
            PaperChoice(
                title = stringResource(R.string.scope_opt_all),
                selected = scope.reach == PracticeScope.Reach.ALL,
                onClick = { onChange(scope.copy(recentAmount = 0, recentUnit = RecencyUnit.PAGES)) }
            )
            PaperChoice(
                title = stringResource(R.string.scope_opt_count),
                selected = scope.reach == PracticeScope.Reach.COUNT,
                onClick = {
                    onChange(
                        scope.copy(
                            recentAmount = scope.recentAmount.takeIf { it > 0 && scope.reach == PracticeScope.Reach.COUNT } ?: PracticeScope.DEFAULT_AMOUNT,
                            recentUnit = RecencyUnit.PAGES
                        )
                    )
                }
            )
            if (scope.reach == PracticeScope.Reach.COUNT) {
                AmountEditor(scope, onChange)
                Hairline()
            }
            PaperChoice(
                title = stringResource(R.string.scope_opt_time),
                selected = scope.reach == PracticeScope.Reach.TIME,
                onClick = {
                    onChange(
                        if (scope.reach == PracticeScope.Reach.TIME) scope
                        else scope.copy(recentAmount = 2, recentUnit = RecencyUnit.WEEKS)
                    )
                }
            )
            if (scope.reach == PracticeScope.Reach.TIME) {
                AmountEditor(scope, onChange)
                Hairline()
            }

            SectionLabel(stringResource(R.string.scope_levels_label), modifier = Modifier.padding(top = 24.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(PlanTier.WEAK, PlanTier.FAIR, PlanTier.STRONG).forEach { tier ->
                    LevelToggle(
                        tier = tier,
                        count = preview?.byTier?.get(tier),
                        selected = tier in scope.tiers,
                        onClick = { onChange(scope.withTier(tier, tier !in scope.tiers)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                text = stringResource(R.string.scope_levels_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(16.dp))
        }

        // The count and the way out stay in view while the choices above scroll.
        Hairline(Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter))
        Column(modifier = Modifier.padding(horizontal = PaperSpace.Gutter).padding(top = 12.dp, bottom = 24.dp)) {
            if (preview != null) {
                if (preview.total == 0) {
                    Text(
                        text = stringResource(R.string.scope_none_match),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = pluralStringResource(R.plurals.scope_will_use, preview.total, preview.total),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            PaperButton(stringResource(R.string.scope_done), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            if (!scope.isAll) {
                PaperLink(
                    text = stringResource(R.string.scope_reset),
                    onClick = { onChange(PracticeScope()) },
                    soft = true,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

/** The number and, for a stretch of time, its unit, with the usual choices a tap away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountEditor(scope: PracticeScope, onChange: (PracticeScope) -> Unit) {
    var typing by remember { mutableStateOf(false) }
    val isTime = scope.reach == PracticeScope.Reach.TIME
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 36.dp, top = 4.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PaperStepper(
                value = scope.recentAmount,
                onChange = { onChange(scope.copy(recentAmount = it)) },
                min = 1,
                max = PracticeScope.MAX_AMOUNT,
                onValueClick = { typing = true }
            )
            if (!isTime) {
                Text(
                    text = stringResource(R.string.practice_scope_unit_pages),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isTime) {
            Row(Modifier.fillMaxWidth()) {
                listOf(
                    RecencyUnit.DAYS to R.string.practice_scope_unit_days,
                    RecencyUnit.WEEKS to R.string.practice_scope_unit_weeks,
                    RecencyUnit.MONTHS to R.string.practice_scope_unit_months,
                    RecencyUnit.YEARS to R.string.practice_scope_unit_years
                ).forEach { (unit, label) ->
                    PaperChip(
                        selected = unit == scope.recentUnit,
                        onClick = { onChange(scope.copy(recentUnit = unit)) },
                        label = { Text(stringResource(label)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val presets = if (isTime) TIME_PRESETS else COUNT_PRESETS.map { it to RecencyUnit.PAGES }
            presets.forEach { (amount, unit) ->
                PaperChip(
                    selected = amount == scope.recentAmount && unit == scope.recentUnit,
                    onClick = { onChange(scope.copy(recentAmount = amount, recentUnit = unit)) },
                    label = { Text(if (isTime) span(unit, amount) else amount.toString()) },
                    // A chip's rule fills its width; without this each one would take a whole row.
                    modifier = Modifier.width(IntrinsicSize.Max)
                )
            }
        }
    }
    if (typing) {
        NumberDialog(
            initial = scope.recentAmount,
            onDone = { amount ->
                typing = false
                if (amount != null) onChange(scope.copy(recentAmount = amount.coerceIn(1, PracticeScope.MAX_AMOUNT)))
            }
        )
    }
}

private val COUNT_PRESETS = listOf(10, 20, 50, 100)
private val TIME_PRESETS = listOf(
    1 to RecencyUnit.WEEKS, 2 to RecencyUnit.WEEKS, 1 to RecencyUnit.MONTHS, 3 to RecencyUnit.MONTHS, 1 to RecencyUnit.YEARS
)

/** Asks for a number; [onDone] gets it, or null when the question is dismissed. */
@Composable
private fun NumberDialog(initial: Int, onDone: (Int?) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    val amount = text.toIntOrNull()?.takeIf { it > 0 }
    PaperDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(stringResource(R.string.scope_number_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> text = input.filter { it.isDigit() }.take(3) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = { TextButton(onClick = { onDone(amount) }, enabled = amount != null) { Text(stringResource(R.string.btn_ok)) } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

/** One level to take pages from: its name, the colour it has elsewhere in the app, and how many pages it has now. */
@Composable
private fun LevelToggle(tier: PlanTier, count: Int?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .heightIn(min = 68.dp)
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp, if (selected) primary else MaterialTheme.colorScheme.outline),
                shape
            )
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(10.dp).background(tierColor(tier), CircleShape))
            Text(
                text = stringResource(tierLabel(tier)),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = count?.toString() ?: "–",
            style = MaterialTheme.typography.titleLarge,
            color = if (count == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground
        )
    }
}
