package com.example.mutqan.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.domain.MistakeTag
import com.example.mutqan.domain.PlanItem
import com.example.mutqan.domain.PlanOrder
import com.example.mutqan.domain.PlanTier
import com.example.mutqan.domain.PlanUnit
import com.example.mutqan.domain.RecallOutcome
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.OrnamentDivider
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperOutlinedButton
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.SectionLabel
import com.example.mutqan.ui.revision.OutcomePicker
import com.example.mutqan.ui.theme.AmiriFontFamily

/** The name of a plan portion in the user's language: "Juz 5", "Page 12" or the surah's name. */
@Composable
fun PlanItem.label(): String = when (unit) {
    PlanUnit.JUZ -> stringResource(R.string.plan_item_juz, number)
    PlanUnit.PAGE ->
        if (endPage > startPage) stringResource(R.string.pages_range, startPage, endPage)
        else stringResource(R.string.plan_item_page, number)
    PlanUnit.SURAH -> SurahData.nameOf(number)
}

/** Today: what the revision plan asks for, plus weak passages worth a look. A page, not a stack of cards. */
@Composable
fun HomeScreen(
    onOpenPlan: () -> Unit,
    onOpenCoverage: () -> Unit,
    onOpenProgress: () -> Unit,
    onTestMyself: (startPage: Int, endPage: Int) -> Unit,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = PaperSpace.Gutter, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
            }
        }
        OrnamentDivider(Modifier.padding(horizontal = PaperSpace.Gutter))

        if (!state.isLoaded) return@Column

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = PaperSpace.Gutter, vertical = PaperSpace.Section)
        ) {
            val plan = state.plan
            val today = state.today
            when {
                !state.hasCoverage -> item {
                    Prompt(
                        title = stringResource(R.string.home_no_coverage_title),
                        body = stringResource(R.string.home_no_coverage_body),
                        action = stringResource(R.string.home_btn_set_coverage),
                        onAction = onOpenCoverage
                    )
                }
                plan == null || today == null -> item {
                    Prompt(
                        title = stringResource(R.string.home_no_plan_title),
                        body = stringResource(R.string.home_no_plan_body),
                        action = stringResource(R.string.home_btn_create_plan),
                        onAction = onOpenPlan
                    )
                }
                else -> {
                    val first = today.due.firstOrNull()
                    item {
                        if (first != null) {
                            SectionLabel(stringResource(R.string.home_due_title))
                            Text(
                                text = first.label(),
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (first.unit != PlanUnit.PAGE) {
                                Text(
                                    text = stringResource(R.string.pages_range, first.startPage, first.endPage),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            first.tier?.let { tier ->
                                Text(
                                    text = stringResource(
                                        when (tier) {
                                            PlanTier.WEAK -> R.string.home_tier_weak
                                            PlanTier.FAIR -> R.string.home_tier_fair
                                            PlanTier.STRONG -> R.string.home_tier_strong
                                        }
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Row(
                                modifier = Modifier.padding(top = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                PaperButton(stringResource(R.string.home_btn_log), onClick = { logging = first })
                                PaperLink(stringResource(R.string.home_btn_open_mushaf), onClick = { onOpenMushaf(first.startPage) })
                                PaperLink(stringResource(R.string.home_btn_test), onClick = { onTestMyself(first.startPage, first.endPage) })
                            }
                        } else {
                            val status = when {
                                today.isPaused -> R.string.home_paused
                                today.isRestDay -> R.string.home_rest_day
                                else -> R.string.home_all_done
                            }
                            Text(
                                text = stringResource(status),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    if (today.due.size > 1) {
                        item { Spacer(Modifier.height(20.dp)) }
                        items(today.due.drop(1), key = { "due-${it.unit}-${it.number}" }) { portion ->
                            PortionRow(portion, done = false, onOpen = { onOpenMushaf(portion.startPage) }, onLog = { logging = portion })
                        }
                    }
                    if (today.done.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(PaperSpace.Section))
                            OrnamentDivider()
                            Spacer(Modifier.height(20.dp))
                            SectionLabel(stringResource(R.string.home_done_title))
                        }
                        items(today.done, key = { "done-${it.unit}-${it.number}" }) { portion ->
                            PortionRow(portion, done = true, onOpen = { onOpenMushaf(portion.startPage) }, onLog = {})
                        }
                    }
                    item {
                        PaperLink(stringResource(R.string.home_btn_progress), onClick = onOpenProgress)
                        PaperLink(
                            text = if (plan.order == PlanOrder.BY_STRENGTH) {
                                stringResource(R.string.home_plan_summary_strength, plan.name, today.dailyPages)
                            } else {
                                stringResource(R.string.home_plan_summary, plan.name, today.cycleDays)
                            },
                            onClick = onOpenPlan,
                            soft = true
                        )
                    }
                }
            }

            if (state.weak.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(PaperSpace.Section))
                    OrnamentDivider()
                    Spacer(Modifier.height(20.dp))
                    SectionLabel(stringResource(R.string.home_weak_title))
                }
                items(state.weak, key = { "weak-${it.passage.id}" }) { weak ->
                    WeakQuote(
                        weak = weak,
                        onOpen = { onOpenMushaf(weak.page) },
                        onDismiss = { viewModel.dismissSuggestion(weak.passage.id) }
                    )
                }
            }

            if (state.hasCoverage) {
                item {
                    Spacer(Modifier.height(PaperSpace.Section))
                    PaperOutlinedButton(stringResource(R.string.home_btn_log_revision), onClick = onLogRevision)
                }
            }
        }
    }
}

@Composable
private fun Prompt(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PaperButton(action, onClick = onAction)
    }
}

/** One portion as a line on the page: name, pages, and what you can do with it. */
@Composable
private fun PortionRow(portion: PlanItem, done: Boolean, onOpen: () -> Unit, onLog: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (done) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = portion.label(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            PaperLink(stringResource(R.string.home_btn_open), onClick = onOpen, soft = done)
            if (!done) PaperLink(stringResource(R.string.home_btn_log), onClick = onLog)
        }
        Hairline()
    }
}

/** A weak passage set like a quotation: a rule down the leading edge, the reference, then the text. */
@Composable
private fun WeakQuote(weak: WeakSuggestion, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val rule = MaterialTheme.colorScheme.tertiary
    val direction = LocalLayoutDirection.current
    Column(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .drawBehind {
                val x = if (direction == LayoutDirection.Rtl) size.width else 0f
                drawLine(rule, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            }
            .padding(start = 14.dp, end = 14.dp)
    ) {
        Text(
            text = stringResource(R.string.saved_ref, SurahData.nameOf(weak.surah), weak.ayah),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = weak.snippet,
            fontFamily = AmiriFontFamily,
            fontSize = 26.sp,
            lineHeight = 48.sp,
            style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PaperLink(stringResource(R.string.home_btn_open), onClick = onOpen)
            PaperLink(stringResource(R.string.home_weak_dismiss), onClick = onDismiss, soft = true)
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
