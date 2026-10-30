package com.example.mutqan.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import com.example.mutqan.ui.components.PaperChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.domain.PlanOrder
import com.example.mutqan.domain.PlanUnit
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperHeader

/** Creates or edits the revision plan: how much per day, which days are rest days, and what comes first. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: PlanViewModel = viewModel(factory = PlanViewModelFactory(context))
    val state by viewModel.uiState.collectAsState()
    val defaultName = stringResource(R.string.plan_default_name)

    Scaffold(
        topBar = { PaperHeader(title = stringResource(R.string.plan_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (!state.isLoaded) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.plan_name)) },
                placeholder = { Text(defaultName) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Label(stringResource(R.string.plan_label_templates))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Template(stringResource(R.string.plan_template_juz1)) { viewModel.applyTemplate(PlanUnit.JUZ, 1) }
                Template(stringResource(R.string.plan_template_juz2)) { viewModel.applyTemplate(PlanUnit.JUZ, 2) }
                Template(stringResource(R.string.plan_template_pages5)) { viewModel.applyTemplate(PlanUnit.PAGE, 5) }
                Template(stringResource(R.string.plan_template_pages10)) { viewModel.applyTemplate(PlanUnit.PAGE, 10) }
            }

            Label(stringResource(R.string.plan_label_unit))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            Label(stringResource(R.string.plan_label_amount, state.amount))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalIconButton(onClick = { viewModel.setAmount(state.amount - 1) }, enabled = state.amount > 1) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                Text(state.amount.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                FilledTonalIconButton(
                    onClick = { viewModel.setAmount(state.amount + 1) },
                    enabled = state.amount < PlanViewModel.maxAmount(state.unit)
                ) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }

            Text(
                text = if (state.itemCount == 0) stringResource(R.string.plan_preview_empty)
                else stringResource(R.string.plan_preview, state.itemCount, state.cycleDays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Label(stringResource(R.string.plan_label_rest))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..7).forEach { day ->
                    PaperChip(
                        selected = day in state.restDays,
                        onClick = { viewModel.toggleRestDay(day) },
                        label = { Text(stringResource(WEEKDAY_LABELS[day - 1])) }
                    )
                }
            }

            Label(stringResource(R.string.plan_label_order))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.plan_label_paused), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = state.paused, onCheckedChange = viewModel::setPaused)
            }

            PaperButton(
                text = stringResource(R.string.plan_btn_save),
                onClick = { viewModel.save(defaultName, onBack) },
                modifier = Modifier.fillMaxWidth()
            )

            if (state.isExisting) {
                TextButton(
                    onClick = { viewModel.delete(onBack) },
                    modifier = Modifier.fillMaxWidth()
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

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Template(text: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(text) })
}
