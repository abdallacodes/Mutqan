package com.example.mutqan.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.domain.MistakeTag
import com.example.mutqan.domain.PlanItem
import com.example.mutqan.domain.PlanUnit
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.ui.revision.OutcomePicker
import com.example.mutqan.ui.theme.AmiriFontFamily

/** The name of a plan portion in the user's language: "Juz 5", "Page 12" or the surah's name. */
@Composable
fun PlanItem.label(): String = when (unit) {
    PlanUnit.JUZ -> stringResource(R.string.plan_item_juz, number)
    PlanUnit.PAGE -> stringResource(R.string.plan_item_page, number)
    PlanUnit.SURAH -> SurahData.nameOf(number)
}

/** Today: what the revision plan asks for, plus weak passages worth a look. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenPlan: () -> Unit,
    onOpenCoverage: () -> Unit,
    onOpenMushaf: (Int) -> Unit,
    onLogRevision: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(context))
    val state by viewModel.uiState.collectAsState()
    var logging by remember { mutableStateOf<PlanItem?>(null) }

    logging?.let { item ->
        LogItemDialog(
            item = item,
            onConfirm = { outcome, tags ->
                viewModel.log(item, outcome, tags)
                logging = null
            },
            onDismiss = { logging = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.home_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (!state.isLoaded) return@Scaffold
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val plan = state.plan
            val today = state.today
            when {
                !state.hasCoverage -> item {
                    PromptCard(
                        title = stringResource(R.string.home_no_coverage_title),
                        body = stringResource(R.string.home_no_coverage_body),
                        action = stringResource(R.string.home_btn_set_coverage),
                        onAction = onOpenCoverage
                    )
                }
                plan == null || today == null -> item {
                    PromptCard(
                        title = stringResource(R.string.home_no_plan_title),
                        body = stringResource(R.string.home_no_plan_body),
                        action = stringResource(R.string.home_btn_create_plan),
                        onAction = onOpenPlan
                    )
                }
                else -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    stringResource(R.string.home_plan_summary, planAmountLabel(plan.unit, plan.amountPerDay), today.cycleDays),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val status = when {
                                    today.isPaused -> R.string.home_paused
                                    today.isRestDay -> R.string.home_rest_day
                                    today.due.isEmpty() -> R.string.home_all_done
                                    else -> null
                                }
                                if (status != null) {
                                    Text(stringResource(status), style = MaterialTheme.typography.bodyMedium)
                                }
                                TextButton(onClick = onOpenPlan) { Text(stringResource(R.string.home_btn_edit_plan)) }
                            }
                        }
                    }
                    if (today.due.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.home_due_title)) }
                        items(today.due, key = { "due-${it.unit}-${it.number}" }) { portion ->
                            PortionCard(
                                portion = portion,
                                done = false,
                                onOpen = { onOpenMushaf(portion.startPage) },
                                onLog = { logging = portion }
                            )
                        }
                    }
                    if (today.done.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.home_done_title)) }
                        items(today.done, key = { "done-${it.unit}-${it.number}" }) { portion ->
                            PortionCard(portion = portion, done = true, onOpen = { onOpenMushaf(portion.startPage) }, onLog = {})
                        }
                    }
                }
            }

            if (state.weak.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.home_weak_title)) }
                items(state.weak, key = { "weak-${it.passage.id}" }) { weak ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(R.string.saved_ref, SurahData.nameOf(weak.surah), weak.ayah),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                weak.snippet,
                                fontFamily = AmiriFontFamily,
                                fontSize = 18.sp,
                                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Rtl),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onOpenMushaf(weak.page) }) { Text(stringResource(R.string.home_btn_open)) }
                                TextButton(onClick = { viewModel.dismissSuggestion(weak.passage.id) }) {
                                    Text(stringResource(R.string.home_weak_dismiss))
                                }
                            }
                        }
                    }
                }
            }

            if (state.hasCoverage) {
                item {
                    OutlinedButton(onClick = onLogRevision, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.home_btn_log_revision))
                    }
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp)) }
        }
    }
}

@Composable
private fun planAmountLabel(unit: PlanUnit, amount: Int): String {
    val unitName = when (unit) {
        PlanUnit.JUZ -> stringResource(R.string.plan_unit_juz)
        PlanUnit.PAGE -> stringResource(R.string.plan_unit_page)
        PlanUnit.SURAH -> stringResource(R.string.plan_unit_surah)
    }
    return "$amount $unitName"
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun PromptCard(title: String, body: String, action: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun PortionCard(portion: PlanItem, done: Boolean, onOpen: () -> Unit, onLog: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (done) 0.5f else 1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (done) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(portion.label(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (portion.unit != PlanUnit.PAGE) {
                    Text(
                        stringResource(R.string.pages_range, portion.startPage, portion.endPage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onOpen) { Text(stringResource(R.string.home_btn_open)) }
            if (!done) Button(onClick = onLog) { Text(stringResource(R.string.home_btn_log)) }
        }
    }
}

@Composable
private fun LogItemDialog(item: PlanItem, onConfirm: (RecallOutcome, Set<MistakeTag>) -> Unit, onDismiss: () -> Unit) {
    var outcome by remember { mutableStateOf(RecallOutcome.INDEPENDENT) }
    var tags by remember { mutableStateOf(emptySet<MistakeTag>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_log_dialog_title, item.label())) },
        text = {
            OutcomePicker(
                outcome = outcome,
                tags = tags,
                onOutcomeChange = { outcome = it },
                onTagsChange = { tags = it }
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(outcome, tags) }) { Text(stringResource(R.string.home_btn_log)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}
