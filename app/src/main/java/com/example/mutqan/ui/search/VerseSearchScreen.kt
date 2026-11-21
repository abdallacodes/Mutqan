package com.example.mutqan.ui.search

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.domain.GoTo
import com.example.mutqan.domain.MatchWindow
import com.example.mutqan.domain.PlanTier
import com.example.mutqan.domain.SearchQuery
import com.example.mutqan.domain.SearchText
import com.example.mutqan.domain.SurahName
import com.example.mutqan.quran.MushafSpan
import com.example.mutqan.quran.PaperLoading
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.components.AyahText
import com.example.mutqan.ui.components.AyahWords
import com.example.mutqan.ui.components.FolioSheetInsets
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.HelpDialog
import com.example.mutqan.ui.components.IndexRow
import com.example.mutqan.ui.components.MushafText
import com.example.mutqan.ui.components.PageGlyphKind
import com.example.mutqan.ui.components.PaperButton
import com.example.mutqan.ui.components.PaperDialog
import com.example.mutqan.ui.components.PaperTabs
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.QuickPeekBottomSheet
import com.example.mutqan.ui.components.QuickPeekTarget
import com.example.mutqan.ui.components.SectionHead
import com.example.mutqan.ui.components.TopBarOverflowMenu
import com.example.mutqan.ui.components.pressScale
import com.example.mutqan.ui.theme.AmiriFontFamily
import com.example.mutqan.ui.theme.DifficultyCritical
import com.example.mutqan.ui.theme.DifficultySmooth
import com.example.mutqan.ui.theme.DifficultyStruggled
import com.example.mutqan.ui.theme.FolioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The verse search. One box takes words of a verse, a surah's name, a page or juz number, or a reference (2:255,
 * البقرة 255); a row of choices keeps it to the whole Quran, the hifz, some juz or one surah. What the box names is
 * offered first as places to go; then every verse with the words, by surah, the words that matched marked, each with
 * its page, which opens the Mushaf on that verse.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VerseSearchScreen(
    onOpenMushaf: (page: Int, ayahId: Int?) -> Unit,
    onOpenRoot: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onBack: (() -> Unit)? = null,
    initialQuery: String = "",
    /** A list to pick verses for (0: a plain search): verses are ticked and added to it, then the screen goes back. */
    pickForList: Int = 0,
    viewModel: VerseSearchViewModel = viewModel(factory = VerseSearchViewModelFactory(LocalContext.current))
) {
    val ui by viewModel.uiState.collectAsState()
    val results by viewModel.results.collectAsState()
    val tiers by viewModel.tiers.collectAsState()
    val compare by viewModel.compare.collectAsState()
    val picking by viewModel.picking.collectAsState()
    val pickMode = pickForList != 0
    // What the list being picked for holds already: those verses are marked and cannot be ticked again.
    val inList: (Int) -> Boolean = remember(picking) {
        val passages = picking?.passages.orEmpty()
        val held: (Int) -> Boolean = { id -> passages.any { id in it } }
        held
    }
    val context = LocalContext.current
    val savedNote = stringResource(R.string.search_group_saved)
    var naming by remember { mutableStateOf(false) }

    var showHelp by remember { mutableStateOf(false) }
    var peek by remember { mutableStateOf<QuickPeekTarget?>(null) }
    var choosingJuz by remember { mutableStateOf(false) }
    var choosingSurah by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()

    LaunchedEffect(initialQuery) { viewModel.start(initialQuery) }
    LaunchedEffect(pickForList) { viewModel.pickFor(pickForList) }
    // Opened to search: the box takes the typing at once (not again on coming back from the Mushaf).
    LaunchedEffect(Unit) {
        if (!focusedOnce) {
            focusedOnce = true
            if (initialQuery.isBlank()) {
                focus.requestFocus()
                keyboard?.show()
            }
        }
    }
    LaunchedEffect(list.isScrollInProgress) { if (list.isScrollInProgress) keyboard?.hide() }
    LaunchedEffect(results.query, ui.scope) { list.scrollToItem(0) }

    fun open(page: Int, ayahId: Int?) {
        viewModel.rememberQuery()
        keyboard?.hide()
        onOpenMushaf(page, ayahId)
    }

    Scaffold(
        topBar = {
            PaperHeader(
                title = if (pickMode) stringResource(R.string.search_pick_title, picking?.name.orEmpty()) else stringResource(R.string.nav_search),
                compact = pickMode,
                onBack = onBack,
                actions = { TopBarOverflowMenu(onHelpClick = { showHelp = true }, onSettingsClick = onSettingsClick) }
            )
        },
        // While comparing, the ticked verses can be saved as one look-alike group.
        bottomBar = {
            if (pickMode && ui.chosen.isNotEmpty()) {
                PickBar(
                    count = ui.chosen.size,
                    onAdd = {
                        keyboard?.hide()
                        viewModel.addPicked { added, name ->
                            val note = if (added == 0) context.getString(R.string.list_added_none, name)
                            else context.resources.getQuantityString(R.plurals.list_added_verses, added, added, name)
                            Toast.makeText(context, note, Toast.LENGTH_SHORT).show()
                            onBack?.invoke()
                        }
                    },
                    onClear = viewModel::clearChosen
                )
            } else if (!pickMode && ui.view != ResultView.SURAH && ui.chosen.isNotEmpty()) {
                SaveBar(count = ui.chosen.size, onSave = { keyboard?.hide(); naming = true }, onClear = viewModel::clearChosen)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            OutlinedTextField(
                value = ui.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PaperSpace.Gutter)
                    .padding(top = 4.dp, bottom = 8.dp)
                    .focusRequester(focus),
                placeholder = { Text(stringResource(R.string.search_field_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (ui.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery(""); focus.requestFocus(); keyboard?.show() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.search_clear_query))
                        }
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); viewModel.rememberQuery() }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            if (pickMode) {
                Text(
                    text = stringResource(R.string.search_pick_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PaperSpace.Gutter).padding(bottom = 4.dp)
                )
            }

            ScopeChips(
                scope = ui.scope,
                hasHifz = ui.hasHifz,
                onAll = { viewModel.setScope(SearchScope.All) },
                onHifz = { viewModel.setScope(SearchScope.Hifz) },
                onJuz = { keyboard?.hide(); choosingJuz = true },
                onSurah = { keyboard?.hide(); choosingSurah = true }
            )

            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)
            ) {
                if (ui.query.isBlank()) {
                    // ── Before typing: the latest searches, and what can be typed ──
                    if (ui.recent.isNotEmpty()) {
                        item(key = "recent-head") {
                            SectionHead(
                                title = stringResource(R.string.search_recent),
                                first = true,
                                actionLabel = stringResource(R.string.search_clear),
                                onAction = viewModel::clearRecent
                            )
                        }
                        items(ui.recent, key = { "recent-$it" }) { text ->
                            RecentRow(text, onClick = { viewModel.setQuery(text) })
                        }
                    }
                    item(key = "try-head") { SectionHead(stringResource(R.string.search_try), first = ui.recent.isEmpty()) }
                    item(key = "tips") {
                        Text(
                            text = stringResource(R.string.search_tips),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 4.dp)
                        )
                    }
                    item(key = "examples") {
                        val examples = listOf(
                            stringResource(R.string.search_example_1),
                            stringResource(R.string.search_example_2),
                            stringResource(R.string.search_example_3)
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            examples.forEach { example ->
                                SearchChip(text = example, selected = false, onClick = { viewModel.setQuery(example) }, kind = ChipKind.ACTION)
                            }
                        }
                    }
                } else {
                    // ── What the box names ──
                    items(results.goTo.size, key = { "goto-$it" }) { i ->
                        val goTo = results.goTo[i]
                        val go = { viewModel.target(goTo).let { (page, ayah) -> open(page, ayah) } }
                        if (goTo is GoTo.Verse) {
                            val ayahId = viewModel.target(goTo).second
                            VerseGoToRow(
                                goToTitle(goTo), ayahId, onClick = go,
                                pick = if (pickMode && ayahId != null) Pick(ayahId in ui.chosen, inList(ayahId)) { viewModel.toggleChosen(ayahId) } else null
                            )
                        } else {
                            IndexRow(
                                title = goToTitle(goTo),
                                glyph = PageGlyphKind.PLAIN,
                                trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                onClick = go
                            )
                        }
                    }
                    items(results.roots, key = { "root-${it.root}" }) { root ->
                        IndexRow(
                            title = stringResource(R.string.root_row_title, root.spaced),
                            description = stringResource(
                                R.string.root_summary,
                                pluralStringResource(R.plurals.count_words, root.words, root.words),
                                pluralStringResource(R.plurals.count_ayahs, root.verses, root.verses)
                            ),
                            trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            onClick = { viewModel.rememberQuery(); keyboard?.hide(); onOpenRoot(root.root) }
                        )
                    }

                    // ── The verses ──
                    if (results.total > 0) {
                        item(key = "count") {
                            val verses = pluralStringResource(R.plurals.count_ayahs, results.total, results.total)
                            Text(
                                // In one surah, its name heads the list just below.
                                text = if (results.groups.size == 1) verses else stringResource(
                                    R.string.search_count,
                                    verses,
                                    pluralStringResource(R.plurals.count_surahs, results.groups.size, results.groups.size)
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = PaperSpace.Gutter).padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                    }
                    if (results.comparable) {
                        item(key = "views") {
                            PaperTabs(
                                labels = listOf(
                                    stringResource(R.string.search_view_surah),
                                    stringResource(R.string.search_view_after),
                                    stringResource(R.string.search_view_before)
                                ),
                                selectedIndex = ui.view.ordinal,
                                onSelect = { viewModel.setView(ResultView.entries[it]) },
                                modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 6.dp)
                            )
                        }
                        if (ui.view != ResultView.SURAH) {
                            item(key = "views-note") {
                                Text(
                                    text = stringResource(if (ui.view == ResultView.AFTER) R.string.search_compare_after_note else R.string.search_compare_before_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    val lined = compare?.takeIf {
                        ui.view != ResultView.SURAH && results.comparable &&
                            it.query == results.query && it.view == ui.view && it.scope == ui.scope
                    }
                    if (lined != null) {
                        items(lined.rows.size, key = { "line-${lined.rows[it].hit.ayahId}" }) { i ->
                            val row = lined.rows[i]
                            val held = pickMode && inList(row.hit.ayahId)
                            CompareRowView(
                                row = row,
                                divider = i > 0 && row.runStart,
                                chosen = held || row.hit.ayahId in ui.chosen,
                                held = held,
                                tier = tiers[row.hit.page],
                                onChoose = { viewModel.toggleChosen(row.hit.ayahId) },
                                onPeek = {
                                    viewModel.rememberQuery()
                                    keyboard?.hide()
                                    peek = QuickPeekTarget(row.hit.ayahId, row.hit.surah, row.hit.ayah, row.hit.page)
                                },
                                onOpenPage = { open(row.hit.page, row.hit.ayahId) }
                            )
                        }
                    } else results.groups.forEach { group ->
                        stickyHeader(key = "surah-${group.surah}") { SurahHeader(group.surah, group.hits.size) }
                        items(group.hits, key = { "ayah-${it.ayahId}" }) { hit ->
                            HitRow(
                                hit = hit,
                                tier = tiers[hit.page],
                                pick = if (pickMode) Pick(hit.ayahId in ui.chosen, inList(hit.ayahId)) { viewModel.toggleChosen(hit.ayahId) } else null,
                                onPeek = {
                                    viewModel.rememberQuery()
                                    keyboard?.hide()
                                    peek = QuickPeekTarget(hit.ayahId, hit.surah, hit.ayah, hit.page)
                                },
                                onOpenPage = { open(hit.page, hit.ayahId) }
                            )
                        }
                    }

                    if (results.total == 0 && results.query == ui.query.trim()) {
                        item(key = "none") {
                            when {
                                results.loading -> Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                                    PaperLoading(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                results.searched && results.goTo.isEmpty() && results.roots.isEmpty() ->
                                    NoVerses(scoped = ui.scope != SearchScope.All, onWiden = { viewModel.setScope(SearchScope.All) })
                                !results.searched && results.goTo.isEmpty() && results.roots.isEmpty() -> Text(
                                    text = stringResource(R.string.search_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(PaperSpace.Gutter)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHelp) {
        HelpDialog(
            title = stringResource(R.string.search_help_title),
            description = stringResource(R.string.search_help_desc),
            onDismiss = { showHelp = false }
        )
    }

    peek?.let { target ->
        QuickPeekBottomSheet(
            target = target,
            onDismiss = { peek = null },
            onOpenMushaf = { page -> open(page, target.verseId) }
        )
    }

    if (naming) {
        GroupNameDialog(
            initialName = results.query,
            onDismiss = { naming = false },
            onConfirm = { name ->
                naming = false
                viewModel.saveGroup(name) { Toast.makeText(context, savedNote, Toast.LENGTH_SHORT).show() }
            }
        )
    }

    if (choosingJuz) {
        JuzSheet(
            chosen = (ui.scope as? SearchScope.Juz)?.juz.orEmpty(),
            onToggle = viewModel::toggleJuz,
            onClear = { viewModel.setScope(SearchScope.All) },
            onDone = { choosingJuz = false }
        )
    }

    if (choosingSurah) {
        SurahSheet(
            chosen = (ui.scope as? SearchScope.Surah)?.surah,
            onPick = { surah ->
                viewModel.setScope(if (surah == null) SearchScope.All else SearchScope.Surah(surah))
                choosingSurah = false
            },
            onDismiss = { choosingSurah = false }
        )
    }
}

@Composable
private fun goToTitle(goTo: GoTo): String = when (goTo) {
    is GoTo.Page -> stringResource(R.string.quran_go_page, goTo.page)
    is GoTo.Surah -> stringResource(R.string.search_goto_surah, SurahData.nameOf(goTo.surah))
    is GoTo.Juz -> stringResource(R.string.search_goto_juz, goTo.juz)
    is GoTo.Verse -> stringResource(R.string.search_goto_verse, SurahData.nameOf(goTo.surah), goTo.ayah)
}

/** A reference typed in the box: where it goes, with the verse itself, so it can be read before it is opened. */
@Composable
private fun VerseGoToRow(title: String, ayahId: Int?, onClick: () -> Unit, pick: Pick? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pick != null) PickBox(pick)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                if (ayahId != null) AyahText(ayahId = ayahId, size = 20.sp, color = MaterialTheme.colorScheme.onSurface, firstWords = MatchWindow.MOST)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Hairline(Modifier.fillMaxWidth())
    }
}

/**
 * A found verse lined up for comparing: a box to tick it for a group, where it is, and the searched words (accent)
 * with what follows or comes before them, the neighbouring verse's words muted. A rule marks where a new run of
 * alike places starts.
 */
@Composable
private fun CompareRowView(
    row: CompareRow,
    divider: Boolean,
    chosen: Boolean,
    tier: PlanTier?,
    /** Already in the list being picked for: shown ticked, and cannot be unticked. */
    held: Boolean = false,
    onChoose: () -> Unit,
    onPeek: () -> Unit,
    onOpenPage: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val colors = remember(row, accent, muted) { row.matchedIds.associateWith { accent } + row.otherIds.associateWith { muted } }
    val chooseLabel = stringResource(R.string.search_choose_cd)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter)) {
        if (divider) {
            Spacer(Modifier.height(6.dp))
            Hairline(Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onPeek)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .toggleable(value = chosen, enabled = !held, role = Role.Checkbox, onValueChange = { onChoose() })
                    .semantics { contentDescription = chooseLabel },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (chosen) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (chosen && !held) accent else muted,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.quiz_ref, SurahData.nameOf(row.hit.surah), row.hit.ayah),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
                MushafText(span = row.span, size = 20.sp, color = MaterialTheme.colorScheme.onSurface, wordColors = colors)
            }
            PagePill(row.hit.page, tier, onClick = onOpenPage)
        }
    }
}

/** A found verse while picking for a list: whether it is ticked, whether the list holds it already, and what ticks it. */
private class Pick(val chosen: Boolean, val held: Boolean, val onToggle: () -> Unit)

/** The tick of a verse being picked, as its own button (in a row whose tap does something else). */
@Composable
private fun PickBox(pick: Pick) {
    val label = stringResource(R.string.search_choose_cd)
    Box(
        modifier = Modifier
            .size(48.dp)
            .toggleable(value = pick.chosen || pick.held, enabled = !pick.held, role = Role.Checkbox, onValueChange = { pick.onToggle() })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) { PickMark(pick) }
}

/** Ticked (accent), already in the list (muted tick), or not ticked. */
@Composable
private fun PickMark(pick: Pick) {
    val on = pick.chosen || pick.held
    val held = stringResource(R.string.list_in_cd)
    Icon(
        imageVector = if (on) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
        contentDescription = if (pick.held) held else null,
        tint = if (pick.chosen && !pick.held) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(22.dp)
    )
}

/** Under the list while verses are picked for a list: add them to it, or untick them all. */
@Composable
private fun PickBar(count: Int, onAdd: () -> Unit, onClear: () -> Unit) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        Hairline(Modifier.fillMaxWidth())
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PaperButton(
                text = pluralStringResource(R.plurals.search_pick_add, count, count),
                onClick = onAdd,
                modifier = Modifier.weight(1f)
            )
            PaperLink(text = stringResource(R.string.search_clear_chosen), onClick = onClear, soft = true)
        }
    }
}

/** Under the list while verses are ticked: save them as a look-alike group (two at least), or untick them all. */
@Composable
private fun SaveBar(count: Int, onSave: () -> Unit, onClear: () -> Unit) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        Hairline(Modifier.fillMaxWidth())
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PaperButton(
                text = pluralStringResource(R.plurals.search_save_chosen, count, count),
                onClick = onSave,
                enabled = count >= 2,
                modifier = Modifier.weight(1f)
            )
            PaperLink(text = stringResource(R.string.search_clear_chosen), onClick = onClear, soft = true)
        }
    }
}

/** Names the new look-alike group; the search words are offered as the name. */
@Composable
private fun GroupNameDialog(initialName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    PaperDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_group_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.search_group_hint)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { PaperLink(text = stringResource(R.string.btn_save), onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) },
        dismissButton = { PaperLink(text = stringResource(R.string.btn_cancel), onClick = onDismiss, soft = true) }
    )
}

// ── The choice of where to look ─────────────────────────────────────────────────

@Composable
private fun ScopeChips(
    scope: SearchScope,
    hasHifz: Boolean,
    onAll: () -> Unit,
    onHifz: () -> Unit,
    onJuz: () -> Unit,
    onSurah: () -> Unit
) {
    val juzLabel = when (scope) {
        is SearchScope.Juz -> {
            val chosen = scope.juz.sorted()
            if (chosen.size <= 2) stringResource(R.string.search_scope_juz_some, chosen.joinToString(stringResource(R.string.scope_list_separator)))
            else pluralStringResource(R.plurals.search_scope_juz_many, chosen.size, chosen.size)
        }
        else -> stringResource(R.string.search_scope_juz)
    }
    val surahLabel = (scope as? SearchScope.Surah)?.let { SurahData.nameOf(it.surah) } ?: stringResource(R.string.search_scope_surah)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = PaperSpace.Gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchChip(stringResource(R.string.search_scope_all), selected = scope == SearchScope.All, onClick = onAll)
        if (hasHifz) SearchChip(stringResource(R.string.search_scope_hifz), selected = scope == SearchScope.Hifz, onClick = onHifz)
        SearchChip(juzLabel, selected = scope is SearchScope.Juz, onClick = onJuz, kind = ChipKind.OPENS)
        SearchChip(surahLabel, selected = scope is SearchScope.Surah, onClick = onSurah, kind = ChipKind.OPENS)
    }
}

private enum class ChipKind {
    /** One of the choices in the row. */
    CHOICE,
    /** Opens a list to choose from; shows a down arrow. */
    OPENS,
    /** Does something once, such as filling the box. */
    ACTION
}

/** A pill like the app's filter pills: tonal when chosen, outlined otherwise. */
@Composable
private fun SearchChip(text: String, selected: Boolean, onClick: () -> Unit, kind: ChipKind = ChipKind.CHOICE) {
    val colors = FolioTheme.colors
    val source = remember { MutableInteractionSource() }
    val content = if (selected) colors.onTonal else MaterialTheme.colorScheme.onSurfaceVariant
    val action = when (kind) {
        ChipKind.CHOICE -> Modifier.selectable(selected = selected, interactionSource = source, indication = null, role = Role.RadioButton, onClick = onClick)
        ChipKind.OPENS -> Modifier.clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick).semantics { this.selected = selected }
        ChipKind.ACTION -> Modifier.clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
    }
    Box(modifier = Modifier.sizeIn(minHeight = 48.dp).then(action), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .pressScale(source)
                .heightIn(min = 36.dp)
                .clip(FolioTheme.shapes.pill)
                .background(if (selected) colors.tonal else colors.folio)
                .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outline, FolioTheme.shapes.pill)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
            if (kind == ChipKind.OPENS) Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        }
    }
}

// ── Rows ────────────────────────────────────────────────────────────────────────

@Composable
private fun RecentRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = PaperSpace.Gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The surah the verses below are in; stays at the top while they scroll. */
@Composable
private fun SurahHeader(surah: Int, count: Int) {
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter).padding(top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = SurahData.nameOf(surah),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = AmiriFontFamily),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = pluralStringResource(R.plurals.count_ayahs, count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Hairline(Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter))
    }
}

/** A found verse: its number, its words with the matched ones marked, and its page, which opens the Mushaf on it. */
@Composable
private fun HitRow(hit: SearchHit, tier: PlanTier?, onPeek: () -> Unit, onOpenPage: () -> Unit, pick: Pick? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (pick == null) Modifier.clickable(role = Role.Button, onClick = onPeek)
                    else Modifier.toggleable(value = pick.chosen || pick.held, enabled = !pick.held, role = Role.Checkbox, onValueChange = { pick.onToggle() })
                )
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pick != null) PickMark(pick)
            Text(
                text = "${hit.ayah}",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = AmiriFontFamily),
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 30.dp)
            )
            HitVerse(hit, Modifier.weight(1f))
            PagePill(hit.page, tier, onClick = onOpenPage)
        }
        Hairline(Modifier.fillMaxWidth())
    }
}

/** The verse in the Mushaf's script, cut around the match when long, the matched words in the accent colour. */
@Composable
private fun HitVerse(hit: SearchHit, modifier: Modifier) {
    val context = LocalContext.current
    val words by produceState(AyahWords.cached(hit.ayahId), hit.ayahId) {
        value = withContext(Dispatchers.IO) { AyahWords.read(QuranRepository.getInstance(context), hit.ayahId) }
    }
    val shown = words
    if (shown.isNullOrEmpty()) {
        Spacer(modifier.height(40.dp))
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    val span = remember(shown, hit.matched) {
        val window = MatchWindow.of(shown.size, hit.matched)
        (if (window.first > 0) MushafSpan.More else MushafSpan.Empty) +
            MushafSpan.of(shown.subList(window.first, window.last + 1)) +
            (if (window.last < shown.lastIndex) MushafSpan.More else MushafSpan.Empty)
    }
    val colors = remember(shown, hit.matched, accent) {
        shown.filter { w -> hit.matched.any { w.position in it } }.associate { it.id to accent }
    }
    MushafText(span = span, modifier = modifier, size = 20.sp, color = MaterialTheme.colorScheme.onSurface, wordColors = colors)
}

/** "p. 26", with a dot in the page's level when it is memorised; opens the Mushaf on the verse. */
@Composable
private fun PagePill(page: Int, tier: PlanTier?, onClick: () -> Unit) {
    val label = stringResource(R.string.page_n, page)
    val description = stringResource(R.string.search_open_page, page) + (tier?.let { " · " + tierLabel(it) } ?: "")
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(FolioTheme.shapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.outline, FolioTheme.shapes.pill)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (tier != null) Box(Modifier.size(8.dp).background(tierColor(tier), CircleShape))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun NoVerses(scoped: Boolean, onWiden: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(PaperSpace.Gutter),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(if (scoped) R.string.search_none_scoped else R.string.search_none),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (scoped) PaperLink(text = stringResource(R.string.search_widen), onClick = onWiden)
    }
}

@Composable
private fun tierColor(tier: PlanTier): Color = when (tier) {
    PlanTier.WEAK -> DifficultyCritical
    PlanTier.FAIR -> DifficultyStruggled
    PlanTier.STRONG -> DifficultySmooth
}

@Composable
private fun tierLabel(tier: PlanTier): String = stringResource(
    when (tier) {
        PlanTier.WEAK -> R.string.strength_weak
        PlanTier.FAIR -> R.string.strength_fair
        PlanTier.STRONG -> R.string.strength_strong
    }
)

// ── Sheets ──────────────────────────────────────────────────────────────────────

/** The thirty juz as a grid; any number can be chosen, and the search keeps to them as they are tapped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JuzSheet(chosen: Set<Int>, onToggle: (Int) -> Unit, onClear: () -> Unit, onDone: () -> Unit) {
    ModalBottomSheet(
        contentWindowInsets = FolioSheetInsets,
        onDismissRequest = onDone,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = PaperSpace.Gutter).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.search_juz_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                if (chosen.isNotEmpty()) PaperLink(text = stringResource(R.string.search_clear), onClick = onClear, soft = true)
            }
            Spacer(Modifier.height(4.dp))
            for (row in 0 until JUZ_ROWS) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (col in 0 until JUZ_COLUMNS) {
                        val juz = row * JUZ_COLUMNS + col + 1
                        JuzCell(juz, selected = juz in chosen, onClick = { onToggle(juz) }, modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            PaperButton(text = stringResource(R.string.scope_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
        }
    }
}

private const val JUZ_ROWS = 5
private const val JUZ_COLUMNS = 6

@Composable
private fun JuzCell(juz: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = FolioTheme.colors
    val name = stringResource(R.string.plan_item_juz, juz)
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.tonal else colors.folio)
            .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$juz",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = AmiriFontFamily),
            color = if (selected) colors.onTonal else MaterialTheme.colorScheme.onBackground
        )
    }
}

/** The surahs, with a box to find one by name or number; a pick keeps the search to it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SurahSheet(chosen: Int?, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    val names = remember { SurahData.ALL.map { SurahName(it.id, it.nameArabic, it.nameEnglish, 0) } }
    val shown = remember(filter) {
        val number = SearchText.digits(filter).trim().toIntOrNull()
        when {
            filter.isBlank() -> names
            number != null -> names.filter { it.id == number }
            else -> SearchQuery.surahsNamed(filter, names)
        }
    }
    ModalBottomSheet(
        contentWindowInsets = FolioSheetInsets,
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.85f).navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.search_surah_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = PaperSpace.Gutter)
            )
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_surah_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (filter.isBlank()) {
                    item(key = "all") {
                        SurahChoice(number = null, name = stringResource(R.string.all_surahs), selected = chosen == null, onClick = { onPick(null) })
                    }
                }
                items(shown, key = { it.id }) { s ->
                    SurahChoice(number = s.id, name = SurahData.nameOf(s.id), selected = chosen == s.id, onClick = { onPick(s.id) })
                }
            }
        }
    }
}

@Composable
private fun SurahChoice(number: Int?, name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = PaperSpace.Gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = number?.toString() ?: "",
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = AmiriFontFamily),
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 30.dp)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}
