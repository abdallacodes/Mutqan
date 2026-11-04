package com.example.mutqan.ui.coverage

import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.quran.ArabicNormalization
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.mutqan.ui.components.PaperChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.domain.PlanTier
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.theme.DifficultyCritical
import com.example.mutqan.ui.theme.DifficultySmooth
import com.example.mutqan.ui.theme.DifficultyStruggled
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import com.example.mutqan.ui.components.PaperDialog
import com.example.mutqan.ui.components.PaperOutlinedButton
import com.example.mutqan.ui.components.PaperSpace

/** Where the user says which parts of the Quran they have memorised. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CoverageScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val viewModel: CoverageViewModel = viewModel(factory = CoverageViewModelFactory(context))
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showSurahPicker by remember { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var expandedJuz by rememberSaveable { mutableStateOf(emptySet<Int>()) }
    var pending by remember { mutableStateOf<PendingRate?>(null) }
    // The level a tap on a page gives it; null clears the page's level.
    var brush by rememberSaveable { mutableStateOf<Int?>(PlanTier.FAIR.code) }

    if (showSurahPicker) {
        SurahPickerDialog(
            full = state.fullSurahs,
            partial = state.partialSurahs,
            onToggle = viewModel::toggleSurah,
            onDone = { showSurahPicker = false }
        )
    }

    pending?.let { ask ->
        PaperDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.coverage_choice_title)) },
            text = { Text(stringResource(R.string.coverage_choice_body, ask.ownCount, ask.pageCount)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    ask.apply(viewModel, false)
                }) { Text(stringResource(R.string.coverage_choice_all)) }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = {
                        pending = null
                        ask.apply(viewModel, true)
                    }) { Text(stringResource(R.string.coverage_choice_unrated)) }
                    TextButton(onClick = { pending = null }) { Text(stringResource(R.string.btn_cancel)) }
                }
            }
        )
    }

    if (showAddDialog) {
        AddRangeDialog(
            onDismiss = { showAddDialog = false },
            onApply = viewModel::applyRange,
            onApplied = { showAddDialog = false }
        )
    }

    Scaffold(
        topBar = {
            PaperHeader(
                title = stringResource(R.string.coverage_title),
                onBack = onDone
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = PaperSpace.Gutter),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (state.isSet) stringResource(R.string.coverage_summary, state.ayahCount, state.pageCount)
                        else stringResource(R.string.coverage_unset_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (state.isSet && state.levelMix.total > 0) {
                        Text(
                            text = mixCaption(state.levelMix),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        LevelHeatmap(state.memorisedPages, state.currentLevels)
                        Text(
                            text = stringResource(R.string.coverage_levels_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaperOutlinedButton(
                        text = stringResource(R.string.coverage_whole_quran),
                        onClick = viewModel::setWholeQuran,
                        modifier = Modifier.weight(1f)
                    )
                    PaperOutlinedButton(
                        text = stringResource(R.string.coverage_clear),
                        onClick = viewModel::clear,
                        enabled = state.isSet,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                PaperOutlinedButton(
                    text = stringResource(R.string.coverage_choose_surahs),
                    onClick = { showSurahPicker = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { SectionTitle(stringResource(R.string.coverage_juz_section)) }
            item {
                Text(
                    text = stringResource(R.string.coverage_strength_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items((1..30).toList(), key = { it }) { juz ->
                val memorised = juz in state.fullJuz
                val row = state.strengths.firstOrNull { it.juz == juz }
                val open = juz in expandedJuz
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(role = Role.Checkbox) { viewModel.toggleJuz(juz) }
                            .padding(vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = if (memorised) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (memorised) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                text = stringResource(R.string.mushaf_juz_row, juz),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (memorised) FontWeight.Bold else FontWeight.Normal
                            )
                            if (row != null) MixLine(row.mix)
                        }
                    }
                    TierChips(
                        selected = if (memorised) row?.tier else null,
                        onSelect = { tier ->
                            if (row != null && row.needsChoice) {
                                pending = PendingRate(tier, row.pages.count { it in state.memorisedPages }, row.pages.count { it in state.pageLevels }) { vm, only ->
                                    vm.rateJuz(juz, tier, only)
                                }
                            } else {
                                viewModel.rateJuz(juz, tier)
                            }
                        }
                    )
                }
                if (row != null) {
                    PaperLink(
                        text = stringResource(if (open) R.string.coverage_hide_pages else R.string.coverage_show_pages),
                        onClick = { expandedJuz = if (open) expandedJuz - juz else expandedJuz + juz },
                        soft = true
                    )
                }
                if (open && row != null) {
                    JuzPages(
                        row = row,
                        memorised = state.memorisedPages,
                        levels = state.pageLevels,
                        brush = brush,
                        onBrush = { brush = it },
                        onPage = { page -> viewModel.ratePage(page, brush?.let { PlanTier.fromCode(it) }) }
                    )
                }
                Hairline()
            }
            item {
                PaperLink(
                    text = stringResource(if (advanced) R.string.coverage_advanced_hide else R.string.coverage_advanced_show),
                    onClick = { advanced = !advanced }
                )
            }
            if (advanced) {
                item { SectionTitle(stringResource(R.string.coverage_surah_levels)) }
                item {
                    Text(
                        text = stringResource(R.string.coverage_surah_levels_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(state.surahStrengths, key = { "surah-${it.surah}" }) { row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                            Text(text = SurahData.nameOf(row.surah), style = MaterialTheme.typography.bodyLarge)
                            MixLine(row.mix)
                        }
                        TierChips(
                            selected = row.tier,
                            onSelect = { tier ->
                                if (row.needsChoice) {
                                    val total = state.surahPageCounts[row.surah] ?: row.mix.total
                                    pending = PendingRate(tier, total, state.surahRatedCounts[row.surah] ?: 0) { vm, only ->
                                        vm.rateSurah(row.surah, tier, only)
                                    }
                                } else {
                                    viewModel.rateSurah(row.surah, tier)
                                }
                            }
                        )
                    }
                    Hairline()
                }
                item { SectionTitle(stringResource(R.string.coverage_page_levels)) }
                item {
                    Text(
                        text = stringResource(R.string.coverage_page_levels_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item { BrushChips(brush) { brush = it } }
                items((0 until PAGE_ROWS).toList(), key = { "pages-$it" }) { row ->
                    // Page numbers keep their left-to-right order, as on any grid of numbers.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                            for (column in 0 until PAGE_COLUMNS) {
                                val page = row * PAGE_COLUMNS + column + 1
                                if (page > TOTAL_PAGES) {
                                    Spacer(Modifier.weight(1f))
                                } else {
                                    PageCell(
                                        page = page,
                                        memorised = page in state.memorisedPages,
                                        tier = state.pageLevels[page],
                                        onClick = { viewModel.ratePage(page, brush?.let { PlanTier.fromCode(it) }) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.coverage_ranges_section)) }
            if (state.ranges.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.coverage_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(state.ranges, key = { it.range.first }) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.coverage_range_label,
                            SurahData.nameOf(item.startSurah), item.startAyah,
                            SurahData.nameOf(item.endSurah), item.endAyah
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.removeRange(item.range) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.coverage_cd_remove_range))
                    }
                }
            }
            item {
                PaperOutlinedButton(
                    text = stringResource(R.string.coverage_add_range),
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                PaperButton(text = stringResource(R.string.coverage_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Every surah in a list to tick off; ticking marks the whole surah as memorised, ticking again takes it away. */
@Composable
private fun SurahPickerDialog(
    full: Set<Int>,
    partial: Set<Int>,
    onToggle: (Int) -> Unit,
    onDone: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val needle = ArabicNormalization.normalizeForSearch(query.trim())
    val surahs = remember(needle) {
        (1..114).filter { id ->
            needle.isEmpty() || id.toString() == needle ||
                ArabicNormalization.normalizeForSearch(SurahData.nameOf(id)).contains(needle) ||
                SurahData.nameOf(id).contains(query.trim(), ignoreCase = true)
        }
    }
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                PaperHeader(
                    title = stringResource(R.string.coverage_choose_surahs),
                    subtitle = stringResource(R.string.coverage_surah_picker_hint),
                    onBack = onDone
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.coverage_surah_search)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 8.dp)
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(surahs, key = { it }) { id ->
                        val isFull = id in full
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Checkbox) { onToggle(id) }
                                .padding(horizontal = PaperSpace.Gutter, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$id",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.width(40.dp)
                            )
                            Text(
                                text = SurahData.nameOf(id),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            when {
                                isFull -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                id in partial -> Text(
                                    text = stringResource(R.string.coverage_surah_partly),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                else -> Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Hairline()
                    }
                }
                Hairline()
                PaperButton(
                    text = stringResource(R.string.coverage_done),
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 10.dp)
                )
            }
        }
    }
}

/** Weak, fair and strong side by side; [selected] is the level the row's pages have, if they share one. */
@Composable
private fun TierChips(selected: PlanTier?, onSelect: (PlanTier) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            PlanTier.WEAK to R.string.strength_weak,
            PlanTier.FAIR to R.string.strength_fair,
            PlanTier.STRONG to R.string.strength_strong
        ).forEach { (tier, label) ->
            PaperChip(
                selected = selected == tier,
                onClick = { onSelect(tier) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.width(64.dp)
            )
        }
    }
}

/** A level the user asked for on a row whose pages already differ, held while they say how to apply it. */
private class PendingRate(
    val tier: PlanTier,
    val pageCount: Int,
    val ownCount: Int,
    val apply: (CoverageViewModel, Boolean) -> Unit
)

@Composable
private fun PlanTier.color(): Color = when (this) {
    PlanTier.WEAK -> DifficultyCritical
    PlanTier.FAIR -> DifficultyStruggled
    PlanTier.STRONG -> DifficultySmooth
}

@Composable
private fun PlanTier.label(): String = stringResource(
    when (this) {
        PlanTier.WEAK -> R.string.strength_weak
        PlanTier.FAIR -> R.string.strength_fair
        PlanTier.STRONG -> R.string.strength_strong
    }
)

/** "Weak: 4 · Fair: 10 · Strong: 6", leaving out the levels no page has. */
@Composable
private fun mixCaption(mix: LevelMix): String {
    val parts = listOf(PlanTier.WEAK, PlanTier.FAIR, PlanTier.STRONG).filter { mix[it] > 0 }
        .map { stringResource(R.string.coverage_mix_part, it.label(), mix[it]) }
    return parts.joinToString(" · ")
}

/** A thin bar split by how many pages are weak, fair and strong, and the same in words under it. */
@Composable
private fun MixLine(mix: LevelMix) {
    if (mix.total == 0) return
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp)) {
        listOf(PlanTier.WEAK, PlanTier.FAIR, PlanTier.STRONG).forEach { tier ->
            if (mix[tier] > 0) Box(Modifier.weight(mix[tier].toFloat()).fillMaxHeight().background(tier.color()))
        }
    }
    Text(
        text = mixCaption(mix),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp)
    )
}

/** All 604 pages as small squares, coloured by where each stands now; pages not memorised are only outlined. */
@Composable
private fun LevelHeatmap(memorised: Set<Int>, levels: Map<Int, PlanTier>) {
    val weak = DifficultyCritical
    val fair = DifficultyStruggled
    val strong = DifficultySmooth
    val faint = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val columns = 40
    val rows = (TOTAL_PAGES + columns - 1) / columns
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(columns.toFloat() / rows)
    ) {
        val cell = size.width / columns
        val gap = 1.dp.toPx()
        for (page in 1..TOTAL_PAGES) {
            val x = ((page - 1) % columns) * cell
            val y = ((page - 1) / columns) * cell
            val color = if (page in memorised) when (levels[page]) {
                PlanTier.WEAK -> weak
                PlanTier.STRONG -> strong
                else -> fair
            } else faint
            drawRect(
                color,
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(cell - gap, cell - gap)
            )
        }
    }
}

/** The pages of one juz: a level to paint with, the grid, and which pages a neighbouring juz shares. */
@Composable
private fun JuzPages(
    row: JuzStrength,
    memorised: Set<Int>,
    levels: Map<Int, PlanTier>,
    brush: Int?,
    onBrush: (Int?) -> Unit,
    onPage: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.coverage_page_levels_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BrushChips(brush, onBrush)
        row.pages.chunked(PAGE_COLUMNS).forEach { chunk ->
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                    chunk.forEach { page ->
                        PageCell(page, page in memorised, levels[page], { onPage(page) }, Modifier.weight(1f))
                    }
                    repeat(PAGE_COLUMNS - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        row.sharedPages.entries.take(2).forEach { (page, other) ->
            Text(
                text = stringResource(R.string.coverage_shared_page, page, other),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Weak, fair, strong and "no level": what a tap on a page sets. */
@Composable
private fun BrushChips(brush: Int?, onBrush: (Int?) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf(
            PlanTier.WEAK.code to R.string.strength_weak,
            PlanTier.FAIR.code to R.string.strength_fair,
            PlanTier.STRONG.code to R.string.strength_strong,
            null to R.string.coverage_brush_clear
        ).forEach { (code, label) ->
            PaperChip(
                selected = brush == code,
                onClick = { onBrush(code) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private const val TOTAL_PAGES = 604
private const val PAGE_COLUMNS = 10
private const val PAGE_ROWS = (TOTAL_PAGES + PAGE_COLUMNS - 1) / PAGE_COLUMNS

/** One page of the grid: coloured by its level, faded when it is not memorised. */
@Composable
private fun PageCell(page: Int, memorised: Boolean, tier: PlanTier?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fill = when {
        !memorised -> Color.Transparent
        tier == PlanTier.WEAK -> DifficultyCritical
        tier == PlanTier.FAIR -> DifficultyStruggled
        tier == PlanTier.STRONG -> DifficultySmooth
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .background(fill)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (memorised) 0.6f else 0.25f))
            .clickable(enabled = memorised, role = Role.Button, onClick = onClick)
    ) {
        Text(
            text = "$page",
            style = MaterialTheme.typography.labelSmall,
            color = when {
                !memorised -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                tier != null -> Color.White
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddRangeDialog(
    onDismiss: () -> Unit,
    onApply: suspend (RangeMode, String, String, String, String, Boolean) -> Boolean,
    onApplied: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(RangeMode.SURAH) }
    var from by remember { mutableStateOf("") }
    var fromAyah by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var toAyah by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    fun submit(remove: Boolean) {
        scope.launch {
            if (onApply(mode, from, fromAyah, to, toAyah, remove)) onApplied() else invalid = true
        }
    }

    val primaryLabel = stringResource(
        if (mode == RangeMode.PAGE) R.string.coverage_field_page else R.string.coverage_field_surah
    )

    PaperDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.coverage_add_range)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        RangeMode.SURAH to R.string.coverage_by_surah,
                        RangeMode.PAGE to R.string.coverage_by_page,
                        RangeMode.AYAH to R.string.coverage_by_ayah
                    ).forEach { (option, label) ->
                        PaperChip(
                            selected = mode == option,
                            onClick = { mode = option; invalid = false },
                            label = { Text(stringResource(label)) }
                        )
                    }
                }
                Text(stringResource(R.string.coverage_from), style = MaterialTheme.typography.labelMedium)
                NumberRow(primaryLabel, from, { from = it; invalid = false }, mode == RangeMode.AYAH, fromAyah) {
                    fromAyah = it; invalid = false
                }
                Text(stringResource(R.string.coverage_to), style = MaterialTheme.typography.labelMedium)
                NumberRow(primaryLabel, to, { to = it; invalid = false }, mode == RangeMode.AYAH, toAyah) {
                    toAyah = it; invalid = false
                }
                if (invalid) {
                    Text(
                        text = stringResource(R.string.coverage_range_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { submit(remove = true) }) { Text(stringResource(R.string.coverage_btn_remove)) }
                TextButton(onClick = { submit(remove = false) }) { Text(stringResource(R.string.coverage_btn_add)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
private fun NumberRow(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    withAyah: Boolean,
    ayahValue: String,
    onAyahValue: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValue(it.filter(Char::isDigit).take(3)) },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        if (withAyah) {
            OutlinedTextField(
                value = ayahValue,
                onValueChange = { onAyahValue(it.filter(Char::isDigit).take(3)) },
                label = { Text(stringResource(R.string.coverage_field_ayah)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
