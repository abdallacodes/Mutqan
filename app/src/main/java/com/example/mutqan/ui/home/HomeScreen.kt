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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.example.mutqan.domain.audio.RecitationController
import com.example.mutqan.ui.audio.rememberAskForNotifications
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
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
import com.example.mutqan.domain.update.UpdateManager
import com.example.mutqan.ui.update.UpdateCard
import com.example.mutqan.ui.update.isOffered
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.HubRow
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
import com.example.mutqan.ui.components.PaperDialog
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.mutqan.ui.navigation.OnTabReselected
import com.example.mutqan.ui.navigation.Routes
import com.example.mutqan.ui.components.AyahText
import com.example.mutqan.ui.components.LocalDockInset
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.text.font.FontWeight
import com.example.mutqan.ui.components.Folio
import com.example.mutqan.ui.components.FolioDivider
import com.example.mutqan.ui.components.FolioGroup
import com.example.mutqan.ui.components.HeroButton
import com.example.mutqan.ui.components.HeroCard
import com.example.mutqan.ui.components.PageStrip
import com.example.mutqan.ui.components.PrimaryButton
import com.example.mutqan.ui.components.TonalButton
import com.example.mutqan.ui.theme.FolioTheme
import com.example.mutqan.ui.components.rememberHaptics

private const val LATER_PREVIEW = 3

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
internal fun PlanTier.hint(): String = stringResource(
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onOpenPlan: () -> Unit,
    onOpenCoverage: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenMushaf: (Int) -> Unit,
    onOpenSavedWeak: () -> Unit,
    onLogRevision: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(LocalContext.current))
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var logging by remember { mutableStateOf<PlanItem?>(null) }
    val recitation = remember(context) { RecitationController.get(context) }
    val askForNotifications = rememberAskForNotifications()
    val scope = rememberCoroutineScope()
    var sessionOpen by rememberSaveable { mutableStateOf(false) }
    var laterExpanded by rememberSaveable { mutableStateOf(false) }
    var doneExpanded by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    OnTabReselected(Routes.HOME) { listState.animateScrollToItem(0) }

    // A new version on GitHub is offered on this screen; the app looks at most twice a day.
    val updates = remember(context) { UpdateManager.get(context) }
    val updateState by updates.state.collectAsState()
    LaunchedEffect(Unit) { updates.checkIfDue() }

    val sessionToday = state.today
    if (sessionOpen && sessionToday != null) {
        ReviewSession(
            due = sessionToday.due,
            doneCount = sessionToday.done.size,
            onLog = { item, outcome, tags -> viewModel.log(item, outcome, tags) },
            onOpenMushaf = onOpenMushaf,
            onClose = { sessionOpen = false }
        )
    }

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

    val accent = FolioTheme.colors.heroAccent

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
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = PaperSpace.Gutter, top = 8.dp, end = PaperSpace.Gutter,
                bottom = PaperSpace.Section + LocalDockInset.current
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (updateState.isOffered) {
                item(key = "update") { UpdateCard(updateState, updates) }
            }
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
                        if (first != null) {
                            // The one deep card of the screen: what is due now, how far the day is, and the button that starts it.
                            HeroCard(modifier = Modifier.fillMaxWidth()) {
                                if (total > 0) {
                                    Text(
                                        text = stringResource(R.string.home_progress_today, today.done.size, total),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = accent
                                    )
                                    PageStrip(
                                        fraction = today.done.size.toFloat() / total,
                                        track = accent.copy(alpha = 0.25f),
                                        color = accent,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 18.dp)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.home_due_title),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = accent
                                )
                                Text(
                                    text = first.label(),
                                    style = MaterialTheme.typography.displayMedium
                                )
                                if (first.unit != PlanUnit.PAGE) {
                                    Text(
                                        text = stringResource(R.string.pages_range, first.startPage, first.endPage),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                first.tier?.let { tier ->
                                    Text(
                                        text = tier.hint(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = accent
                                    )
                                }
                                Spacer(Modifier.height(20.dp))
                                if (today.due.size > 1) {
                                    HeroButton(
                                        text = stringResource(R.string.home_btn_start_review, today.due.size),
                                        onClick = { sessionOpen = true },
                                        icon = Icons.Rounded.PlayArrow,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    HeroButton(
                                        text = stringResource(R.string.home_btn_revised),
                                        onClick = { logging = first },
                                        icon = Icons.Default.Check,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                            val status = when {
                                today.isPaused -> R.string.home_paused
                                today.isRestDay -> R.string.home_rest_day
                                else -> R.string.home_all_done
                            }
                            HeroCard(modifier = Modifier.fillMaxWidth()) {
                                if (total > 0) {
                                    Text(
                                        text = stringResource(R.string.home_progress_today, today.done.size, total),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = accent
                                    )
                                    PageStrip(
                                        fraction = today.done.size.toFloat() / total,
                                        track = accent.copy(alpha = 0.25f),
                                        color = accent,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)
                                    )
                                }
                                Text(text = stringResource(status), style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }
                    if (first != null) {
                        item {
                            Column {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (today.due.size > 1) TonalButton(
                                        text = stringResource(R.string.home_btn_revised),
                                        onClick = { logging = first },
                                        modifier = Modifier.weight(1f)
                                    )
                                    TonalButton(
                                        text = stringResource(R.string.home_btn_open_mushaf),
                                        onClick = { onOpenMushaf(first.startPage) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                val reviewPages = today.due.flatMap { it.startPage..it.endPage }
                                FlowRow(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PaperLink(
                                        text = stringResource(R.string.audio_listen_review),
                                        onClick = {
                                            askForNotifications()
                                            scope.launch { recitation.playPages(reviewPages) }
                                        },
                                        soft = true
                                    )
                                    PaperLink(
                                        text = stringResource(R.string.audio_download_review),
                                        onClick = {
                                            askForNotifications()
                                            scope.launch { recitation.downloadPages(reviewPages) }
                                        },
                                        soft = true
                                    )
                                }
                            }
                        }
                    }
                    if (today.due.size > 1) {
                        item {
                            val later = today.due.drop(1)
                            val shown = if (laterExpanded) later else later.take(LATER_PREVIEW)
                            Column {
                                SectionLabel(stringResource(R.string.home_later_title), Modifier.padding(bottom = 10.dp))
                                FolioGroup(Modifier.fillMaxWidth()) {
                                    shown.forEachIndexed { index, portion ->
                                        if (index > 0) FolioDivider(inset = 16.dp)
                                        PortionRow(portion, done = false, onOpen = { onOpenMushaf(portion.startPage) }, onLog = { logging = portion })
                                    }
                                }
                                if (later.size > LATER_PREVIEW) {
                                    PaperLink(
                                        text = if (laterExpanded) stringResource(R.string.home_show_less)
                                        else stringResource(R.string.home_later_more, later.size - LATER_PREVIEW),
                                        onClick = { laterExpanded = !laterExpanded },
                                        soft = true,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (today.done.isNotEmpty() && !doneExpanded) {
                        item {
                            PaperLink(
                                text = stringResource(R.string.home_done_show, today.done.size),
                                onClick = { doneExpanded = true },
                                soft = true
                            )
                        }
                    }
                    if (today.done.isNotEmpty() && doneExpanded) {
                        item {
                            Column {
                                SectionLabel(stringResource(R.string.home_done_title), Modifier.padding(bottom = 10.dp))
                                FolioGroup(Modifier.fillMaxWidth()) {
                                    today.done.forEachIndexed { index, portion ->
                                        if (index > 0) FolioDivider(inset = 16.dp)
                                        PortionRow(portion, done = true, onOpen = { onOpenMushaf(portion.startPage) }, onLog = {})
                                    }
                                }
                                PaperLink(
                                    text = stringResource(R.string.home_show_less),
                                    onClick = { doneExpanded = false },
                                    soft = true,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (state.weak.isNotEmpty()) {
                item {
                    SectionLabel(stringResource(R.string.home_weak_title), Modifier.padding(top = 10.dp))
                }
                items(state.weak, key = { "weak-${it.passage.id}" }) { weak ->
                    WeakQuote(
                        weak = weak,
                        onOpen = { onOpenMushaf(weak.page) },
                        onDismiss = { viewModel.dismissSuggestion(weak.passage.id) }
                    )
                }
                // Only a few are shown; when there are more, other ones and the whole list are one tap away.
                if (state.weakTotal > state.weak.size) {
                    item(key = "weak-more") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            PaperLink(stringResource(R.string.home_weak_other), onClick = viewModel::showOtherWeak)
                            PaperLink(stringResource(R.string.home_weak_all, state.weakTotal), onClick = onOpenSavedWeak)
                        }
                    }
                }
            }

            if (state.missed.isNotEmpty()) {
                item {
                    Column {
                        SectionLabel(stringResource(R.string.home_missed_title), Modifier.padding(top = 10.dp, bottom = 10.dp))
                        FolioGroup(Modifier.fillMaxWidth()) {
                            state.missed.forEachIndexed { index, missed ->
                                if (index > 0) FolioDivider(inset = 16.dp)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(role = Role.Button, onClick = { onOpenMushaf(missed.page) })
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = pluralStringResource(R.plurals.home_missed_line, missed.count, missed.page, missed.count),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    PaperSmallButton(stringResource(R.string.home_btn_open), onClick = { onOpenMushaf(missed.page) })
                                }
                            }
                        }
                    }
                }
            }

            if (state.hasCoverage && plan != null && today != null) {
                item {
                    PlanSummary(
                        name = plan.name,
                        line = if (plan.order == PlanOrder.BY_STRENGTH) {
                            pluralStringResource(R.plurals.home_plan_line_strength, today.dailyPages, today.dailyPages)
                        } else {
                            pluralStringResource(R.plurals.home_plan_line_fixed, today.cycleDays, today.cycleDays)
                        },
                        onEdit = onOpenPlan
                    )
                }
            }

            // Everything else Today can lead to, as cards with an arrow so each one reads as pressable.
            item {
                Column {
                    state.lastPage?.let { page ->
                        HubRow(
                            title = stringResource(R.string.home_continue_title),
                            subtitle = null,
                            note = stringResource(R.string.home_continue_page, page),
                            onClick = { onOpenMushaf(page) },
                            horizontalPadding = 0.dp,
                            compact = true,
                            icon = Icons.Rounded.MenuBook
                        )
                    }
                    if (state.hasCoverage) {
                        HubRow(
                            title = stringResource(R.string.home_btn_progress),
                            subtitle = stringResource(R.string.home_progress_hint),
                            onClick = onOpenProgress,
                            horizontalPadding = 0.dp,
                            compact = true,
                            icon = Icons.Rounded.Insights
                        )
                        HubRow(
                            title = stringResource(R.string.home_btn_log_revision),
                            subtitle = stringResource(R.string.home_log_hint),
                            onClick = onLogRevision,
                            horizontalPadding = 0.dp,
                            compact = true,
                            icon = Icons.Rounded.EditNote
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Prompt(title: String, body: String, action: String, onAction: () -> Unit) {
    Folio(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
        PrimaryButton(action, onClick = onAction, modifier = Modifier.fillMaxWidth())
    }
}

/** The plan in one card: its name, what it asks for, and a button that says it can be changed. */
@Composable
private fun PlanSummary(name: String, line: String, onEdit: () -> Unit) {
    Column {
        SectionLabel(stringResource(R.string.home_plan_title), Modifier.padding(bottom = 10.dp))
        Folio(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PaperLink(stringResource(R.string.home_btn_edit_plan), onClick = onEdit)
            }
        }
    }
}

/** One portion as a row. The row opens the Mushaf; the button beside it marks it revised. */
@Composable
private fun PortionRow(portion: PlanItem, done: Boolean, onOpen: () -> Unit, onLog: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                style = MaterialTheme.typography.titleMedium,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
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
}

/** A weak passage on its own card: the reference, then the text, then what to do with it. */
@Composable
private fun WeakQuote(weak: WeakSuggestion, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Folio(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
        Text(
            text = stringResource(R.string.saved_ref, SurahData.nameOf(weak.surah), weak.ayah),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        AyahText(ayahId = weak.ayahId, size = 26.sp, firstWords = 10, maxLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PaperLink(stringResource(R.string.home_btn_open), onClick = onOpen)
            PaperLink(stringResource(R.string.home_weak_dismiss), onClick = onDismiss, soft = true)
        }
    }
}

@Composable
private fun LogItemDialog(item: PlanItem, onConfirm: (RecallOutcome, Set<MistakeTag>) -> Unit, onDismiss: () -> Unit) {
    var outcome by remember { mutableStateOf(RecallOutcome.INDEPENDENT) }
    var tags by remember { mutableStateOf(emptySet<MistakeTag>()) }
    val haptics = rememberHaptics()
    PaperDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_log_dialog_title, item.label())) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutcomePicker(
                    outcome = outcome,
                    tags = tags,
                    onOutcomeChange = { outcome = it },
                    onTagsChange = { tags = it },
                    preview = OutcomePreview(item.startPage..item.endPage)
                )
            }
        },
        confirmButton = { TextButton(onClick = { haptics.confirm(); onConfirm(outcome, tags) }) { Text(stringResource(R.string.home_btn_log)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}
