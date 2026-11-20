package com.example.mutqan.ui.home

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.domain.PlanUnit
import com.example.mutqan.domain.SmartPlan
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperChip
import com.example.mutqan.ui.components.PaperChoice
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.PaperStepper
import com.example.mutqan.ui.components.SectionLabel
import com.example.mutqan.ui.components.PaperSwitch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.mutqan.ui.components.Folio
import com.example.mutqan.ui.components.lifted
import com.example.mutqan.ui.theme.FolioTheme

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
                        .lifted(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(FolioTheme.colors.folio, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = when {
                                state.hifzPages == 0 -> stringResource(R.string.plan_preview_empty)
                                state.byStrength -> stringResource(
                                    R.string.home_plan_line_smart,
                                    pluralStringResource(R.plurals.home_plan_smart_pages, state.pagesPerDay, state.pagesPerDay),
                                    pluralStringResource(R.plurals.home_plan_smart_cycle, state.smartCycle, state.smartCycle)
                                )
                                state.itemCount == 0 -> stringResource(R.string.plan_preview_empty)
                                else -> stringResource(
                                    R.string.plan_preview,
                                    pluralStringResource(R.plurals.plan_preview_units, state.itemCount, state.itemCount),
                                    pluralStringResource(R.plurals.plan_preview_cycle, state.cycleDays, state.cycleDays)
                                )
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

            // By strength first: the app chooses the pages; a fixed rotation is for following a teacher's order.
            Step(1, stringResource(R.string.plan_step_kind))
            PaperChoice(
                title = stringResource(R.string.plan_mode_strength),
                description = stringResource(R.string.plan_mode_strength_desc),
                selected = state.byStrength,
                onClick = { viewModel.setByStrength(true) }
            )
            PaperChoice(
                title = stringResource(R.string.plan_mode_fixed),
                description = stringResource(R.string.plan_mode_fixed_desc),
                selected = !state.byStrength,
                onClick = { viewModel.setByStrength(false) }
            )

            if (state.byStrength) {
                Step(2, stringResource(R.string.plan_smart_question))
                PagesPerDay(state, viewModel::setPagesPerDay)
            } else {
                Step(2, stringResource(R.string.plan_step_amount))
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
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(value = state.paused, role = Role.Switch, onValueChange = viewModel::setPaused),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.plan_label_paused), style = MaterialTheme.typography.bodyLarge)
                    Hint(stringResource(R.string.plan_pause_hint))
                }
                PaperSwitch(checked = state.paused, onCheckedChange = null)
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
 * How many pages a day, for the plan by strength: the steps for this hifz as chips (the last, every page every week,
 * starred), a stepper for any other number, a good start to take, and what the choice gives: how often the whole hifz
 * comes around, and the weak pages.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PagesPerDay(state: PlanUiState, onChange: (Int) -> Unit) {
    if (state.hifzPages == 0) {
        Hint(stringResource(R.string.plan_preview_empty))
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        state.steps.forEach { pages ->
            PaperChip(
                selected = state.pagesPerDay == pages,
                onClick = { onChange(pages) },
                label = { Text(if (pages == state.weeklyPages) stringResource(R.string.plan_smart_week_chip, pages) else pages.toString()) }
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PaperStepper(value = state.pagesPerDay, onChange = onChange, min = 1, max = SmartPlan.MAX_PAGES)
        Text(stringResource(R.string.plan_unit_pages_a_day), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (state.pagesPerDay != state.goodStart) {
        PaperLink(
            text = pluralStringResource(R.plurals.plan_smart_good_start, state.goodStart, state.goodStart),
            onClick = { onChange(state.goodStart) },
            soft = true
        )
    }
    Folio(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(
                R.string.plan_smart_cycle_line,
                pluralStringResource(R.plurals.plan_smart_hifz, state.hifzPages, state.hifzPages),
                pluralStringResource(R.plurals.plan_smart_every, state.smartCycle, state.smartCycle)
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (state.weakEvery > 0) {
            Text(
                text = pluralStringResource(R.plurals.plan_smart_weak_every, state.weakEvery, state.weakEvery),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (state.weeklyPages > 0 && state.pagesPerDay >= state.weeklyPages) {
            Text(
                text = stringResource(R.string.plan_smart_week_goal),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
    // A lot to begin with: a word for keeping it up.
    if (!state.isExisting && state.pagesPerDay > BIG_START) Hint(stringResource(R.string.plan_smart_big_note))
}

/** Above this many pages a day, a new plan is told that starting lower and growing is easier to keep. */
private const val BIG_START = 40

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Template(text: String, onClick: () -> Unit) {
    PaperChip(selected = false, onClick = onClick, label = { Text(text) })
}
