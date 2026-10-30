package com.example.mutqan.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.domain.PlanOrder
import com.example.mutqan.domain.PlanTier
import com.example.mutqan.domain.PlanUnit
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperChip
import com.example.mutqan.ui.components.PaperChoice
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.PaperStepper
import com.example.mutqan.ui.components.SectionLabel

/**
 * Creates or edits the revision plan. Four short steps in the order a person thinks about them:
 * how the plan works, how much per day, which days to rest, and whether it is paused. The Save
 * button and a one-line summary stay at the bottom so it is always clear what the plan asks for.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: PlanViewModel = viewModel(factory = PlanViewModelFactory(context))
    val state by viewModel.uiState.collectAsState()
    val defaultName = stringResource(R.string.plan_default_name)

    Scaffold(
        topBar = { PaperHeader(title = stringResource(R.string.plan_title), onBack = onBack) },
        bottomBar = {
            if (state.isLoaded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Hairline()
                    Column(
                        modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = when {
                                state.byStrength -> stringResource(R.string.plan_strength_total, state.dailyTotal)
                                state.itemCount == 0 -> stringResource(R.string.plan_preview_empty)
                                else -> stringResource(R.string.plan_preview, state.itemCount, state.cycleDays)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PaperButton(
                            text = stringResource(R.string.plan_btn_save),
                            onClick = { viewModel.save(defaultName, onBack) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (!state.isLoaded) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PaperSpace.Gutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.plan_name)) },
                placeholder = { Text(defaultName) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Step(1, stringResource(R.string.plan_step_kind))
            PaperChoice(
                title = stringResource(R.string.plan_mode_fixed),
                description = stringResource(R.string.plan_mode_fixed_desc),
                selected = !state.byStrength,
                onClick = { viewModel.setByStrength(false) }
            )
            PaperChoice(
                title = stringResource(R.string.plan_mode_strength),
                description = stringResource(R.string.plan_mode_strength_desc),
                selected = state.byStrength,
                onClick = { viewModel.setByStrength(true) }
            )

            Step(2, stringResource(R.string.plan_step_amount))
            if (state.byStrength) {
                listOf(PlanTier.WEAK, PlanTier.FAIR, PlanTier.STRONG).forEach { tier ->
                    TierRow(
                        tier = tier,
                        perDay = state.pagesOf(tier),
                        pages = state.tierCounts[tier] ?: 0,
                        cycleDays = state.cycleOf(tier),
                        onChange = { viewModel.setTierPages(tier, it) }
                    )
                    Hairline()
                }
            } else {
                Hint(stringResource(R.string.plan_label_templates))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Template(stringResource(R.string.plan_template_juz1)) { viewModel.applyTemplate(PlanUnit.JUZ, 1) }
                    Template(stringResource(R.string.plan_template_juz2)) { viewModel.applyTemplate(PlanUnit.JUZ, 2) }
                    Template(stringResource(R.string.plan_template_pages5)) { viewModel.applyTemplate(PlanUnit.PAGE, 5) }
                    Template(stringResource(R.string.plan_template_pages10)) { viewModel.applyTemplate(PlanUnit.PAGE, 10) }
                }

                Spacer(Modifier.height(8.dp))
                Hint(stringResource(R.string.plan_label_unit))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlanUnit.entries.forEach { unit ->
                        PaperChip(
                            selected = state.unit == unit,
                            onClick = { viewModel.setUnit(unit) },
                            label = {
                                Text(
                                    stringResource(
                                        when (unit) {
                                            PlanUnit.JUZ -> R.string.plan_unit_juz
                                            PlanUnit.PAGE -> R.string.plan_unit_page
                                            PlanUnit.SURAH -> R.string.plan_unit_surah
                                        }
                                    )
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Hint(stringResource(R.string.plan_label_amount_each_day))
                PaperStepper(
                    value = state.amount,
                    onChange = viewModel::setAmount,
                    min = 1,
                    max = PlanViewModel.maxAmount(state.unit)
                )

                Spacer(Modifier.height(8.dp))
                Hint(stringResource(R.string.plan_label_order))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaperChip(
                        selected = state.order == PlanOrder.ROTATION,
                        onClick = { viewModel.setOrder(PlanOrder.ROTATION) },
                        label = { Text(stringResource(R.string.plan_order_rotation)) }
                    )
                    PaperChip(
                        selected = state.order == PlanOrder.WEAKEST_FIRST,
                        onClick = { viewModel.setOrder(PlanOrder.WEAKEST_FIRST) },
                        label = { Text(stringResource(R.string.plan_order_weakest)) }
                    )
                }
            }

            Step(3, stringResource(R.string.plan_label_rest))
            Hint(stringResource(R.string.plan_rest_hint))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                (1..7).forEach { day ->
                    PaperChip(
                        selected = day in state.restDays,
                        onClick = { viewModel.toggleRestDay(day) },
                        label = { Text(stringResource(WEEKDAY_LABELS[day - 1])) }
                    )
                }
            }

            Step(4, stringResource(R.string.plan_step_pause))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.plan_label_paused), style = MaterialTheme.typography.bodyLarge)
                    Hint(stringResource(R.string.plan_pause_hint))
                }
                Switch(checked = state.paused, onCheckedChange = viewModel::setPaused)
            }

            if (state.isExisting) {
                TextButton(
                    onClick = { viewModel.delete(onBack) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text(stringResource(R.string.plan_btn_delete), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

/** Monday..Sunday, matching ISO weekday numbers 1..7. */
private val WEEKDAY_LABELS = listOf(
    R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu,
    R.string.day_fri, R.string.day_sat, R.string.day_sun
)

/** A numbered heading that opens one part of the form. */
@Composable
private fun Step(number: Int, title: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        SectionLabel(stringResource(R.string.plan_step_number, number))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
    }
}

/**
 * One strength tier: its name and how many pages it holds, how long one pass takes at the chosen pace,
 * and a stepper for the pages a day it gets.
 */
@Composable
private fun TierRow(tier: PlanTier, perDay: Int, pages: Int, cycleDays: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(
                    when (tier) {
                        PlanTier.WEAK -> R.string.plan_tier_weak
                        PlanTier.FAIR -> R.string.plan_tier_fair
                        PlanTier.STRONG -> R.string.plan_tier_strong
                    }
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Hint(
                when {
                    pages == 0 -> stringResource(R.string.plan_tier_empty)
                    perDay == 0 -> stringResource(R.string.plan_tier_off, pages)
                    else -> stringResource(R.string.plan_tier_cycle, pages, cycleDays)
                }
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PaperStepper(value = perDay, onChange = onChange, min = 0, max = 100)
            Text(
                text = stringResource(R.string.plan_unit_pages_a_day),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Template(text: String, onClick: () -> Unit) {
    PaperChip(selected = false, onClick = onClick, label = { Text(text) })
}
