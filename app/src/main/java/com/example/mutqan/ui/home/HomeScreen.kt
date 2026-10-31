package com.example.mutqan.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.example.mutqan.ui.components.HubRow
import com.example.mutqan.ui.components.OrnamentDivider
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperOutlinedButton
import com.example.mutqan.ui.components.PaperProgress
import com.example.mutqan.ui.components.PaperSmallButton
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.SectionLabel
import com.example.mutqan.ui.revision.OutcomePicker
import com.example.mutqan.ui.revision.OutcomePreview
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

@Composable
private fun PlanTier.hint(): String = stringResource(
    when (this) {
        PlanTier.WEAK -> R.string.home_tier_weak
        PlanTier.FAIR -> R.string.home_tier_fair
        PlanTier.STRONG -> R.string.home_tier_strong
    }
)

/**
 * Today: what the revision plan asks for, how far along the day is, weak passages worth a look,
 * and the plan itself with a clear way to change it. A page, not a stack of cards.
 */
@Composable
fun HomeScreen(
    onOpenPlan: () -> Unit,
    onOpenCoverage: () -> Unit,
    onOpenProgress: () -> Unit,
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

    // The Hijri date leads, since it is the one the plan is lived by; the Gregorian date follows.
    val now = System.currentTimeMillis()
    val locale = LocalConfiguration.current.locales[0]
    val dateLine = hijriDate(now, locale) + " · " + DateUtils.formatDateTime(
        context, now, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PaperHeader(
            title = stringResource(R.string.home_title),
            subtitle = dateLine,
            actions = {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        )

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
                    val total = today.due.size + today.done.size
                    item {
                        if (total > 0) {
                            Text(
                                text = stringResource(R.string.home_progress_today, today.done.size, total),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            PaperProgress(
                                fraction = today.done.size.toFloat() / total,
                                modifier = Modifier.padding(top = 8.dp, bottom = PaperSpace.Section)
                            )
                        }
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
                                    text = tier.hint(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                PaperButton(
                                    text = stringResource(R.string.home_btn_revised),
                                    onClick = { logging = first },
                                    modifier = Modifier.weight(1f)
                                )
                                PaperOutlinedButton(
                                    text = stringResource(R.string.home_btn_open_mushaf),
                                    onClick = { onOpenMushaf(first.startPage) },
                                    modifier = Modifier.weight(1f)
                                )
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
                        item {
                            Spacer(Modifier.height(PaperSpace.Section))
                            SectionLabel(stringResource(R.string.home_later_title))
                            Spacer(Modifier.height(4.dp))
                            Hairline()
                        }
                        items(today.due.drop(1), key = { "due-${it.unit}-${it.number}" }) { portion ->
                            PortionRow(portion, done = false, onOpen = { onOpenMushaf(portion.startPage) }, onLog = { logging = portion })
                        }
                    }
                    if (today.done.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(PaperSpace.Section))
                            SectionLabel(stringResource(R.string.home_done_title))
                            Spacer(Modifier.height(4.dp))
                            Hairline()
                        }
                        items(today.done, key = { "done-${it.unit}-${it.number}" }) { portion ->
                            PortionRow(portion, done = true, onOpen = { onOpenMushaf(portion.startPage) }, onLog = {})
                        }
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

            if (state.missed.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(PaperSpace.Section))
                    OrnamentDivider()
                    Spacer(Modifier.height(20.dp))
                    SectionLabel(stringResource(R.string.home_missed_title))
                    Spacer(Modifier.height(4.dp))
                    Hairline()
                }
                items(state.missed, key = { "missed-${it.page}" }) { missed ->
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button, onClick = { onOpenMushaf(missed.page) })
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.home_missed_line, missed.page, missed.count),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            PaperSmallButton(stringResource(R.string.home_btn_open), onClick = { onOpenMushaf(missed.page) })
                        }
                        Hairline()
                    }
                }
            }

            if (state.hasCoverage && plan != null && today != null) {
                item {
                    Spacer(Modifier.height(PaperSpace.Section))
                    OrnamentDivider()
                    Spacer(Modifier.height(20.dp))
                    PlanSummary(
                        name = plan.name,
                        line = if (plan.order == PlanOrder.BY_STRENGTH) {
                            stringResource(R.string.home_plan_line_strength, today.dailyPages)
                        } else {
                            stringResource(R.string.home_plan_line_fixed, today.cycleDays)
                        },
                        onEdit = onOpenPlan
                    )
                }
            }

            // Everything else Today can lead to, set as rows with an arrow so each one reads as pressable.
            item {
                Spacer(Modifier.height(PaperSpace.Section))
                Hairline()
                state.lastPage?.let { page ->
                    HubRow(
                        title = stringResource(R.string.home_continue_title),
                        subtitle = stringResource(R.string.home_continue_page, page),
                        onClick = { onOpenMushaf(page) },
                        horizontalPadding = 0.dp
                    )
                }
                if (state.hasCoverage) {
                    HubRow(
                        title = stringResource(R.string.home_btn_progress),
                        subtitle = stringResource(R.string.home_progress_hint),
                        onClick = onOpenProgress,
                        horizontalPadding = 0.dp
                    )
                    HubRow(
                        title = stringResource(R.string.home_btn_log_revision),
                        subtitle = stringResource(R.string.home_log_hint),
                        onClick = onLogRevision,
                        horizontalPadding = 0.dp
                    )
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

/** The plan in one block: its name, what it asks for, and a button that says it can be changed. */
@Composable
private fun PlanSummary(name: String, line: String, onEdit: () -> Unit) {
    Column {
        SectionLabel(stringResource(R.string.home_plan_title))
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PaperSmallButton(stringResource(R.string.home_btn_edit_plan), onClick = onEdit)
        }
    }
}

/** One portion as a line on the page. The line opens the Mushaf; the button beside it marks it revised. */
@Composable
private fun PortionRow(portion: PlanItem, done: Boolean, onOpen: () -> Unit, onLog: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(vertical = 10.dp),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = portion.label(),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground
                )
                val sub = when {
                    portion.tier != null -> portion.tier.hint()
                    portion.unit != PlanUnit.PAGE -> stringResource(R.string.pages_range, portion.startPage, portion.endPage)
                    else -> null
                }
                if (sub != null) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!done) PaperSmallButton(stringResource(R.string.home_btn_log), onClick = onLog)
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
                onTagsChange = { tags = it },
                preview = OutcomePreview(item.startPage..item.endPage)
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(outcome, tags) }) { Text(stringResource(R.string.home_btn_log)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}
