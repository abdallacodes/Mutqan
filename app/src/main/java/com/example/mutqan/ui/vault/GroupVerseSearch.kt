package com.example.mutqan.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.domain.GoTo
import com.example.mutqan.quran.PaperLoading
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.search.HitRow
import com.example.mutqan.ui.search.JuzSheet
import com.example.mutqan.ui.search.NoVerses
import com.example.mutqan.ui.search.Pick
import com.example.mutqan.ui.search.ScopeChips
import com.example.mutqan.ui.search.SearchBox
import com.example.mutqan.ui.search.SearchHit
import com.example.mutqan.ui.search.SearchScope
import com.example.mutqan.ui.search.SurahHeader
import com.example.mutqan.ui.search.SurahSheet
import com.example.mutqan.ui.search.VerseSearchViewModel
import com.example.mutqan.ui.search.VerseSearchViewModelFactory

/**
 * The group editor's way to add verses: the app's verse search, picking. Words of a verse, or a surah and verse
 * (البقرة 255, 2:255), kept to the whole Quran, the hifz, some juz or one surah; the verses found are ticked (those in
 * the group already are marked and cannot be), and the bar at the foot puts the ticked ones in the group.
 *
 * @param inGroup the verses the group holds now.
 * @param onAdd puts the ticked verses in the group (the editor then closes this).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GroupVerseSearch(
    inGroup: Set<Int>,
    onAdd: (List<Int>) -> Unit,
    onBack: () -> Unit,
    onOpenMushaf: (Int) -> Unit
) {
    val context = LocalContext.current
    // Kept with the editor, so coming back here finds the last search as it was.
    val search: VerseSearchViewModel = viewModel(key = "group-verse-search", factory = VerseSearchViewModelFactory(context))
    LaunchedEffect(search) { search.pickForGroup() }
    val ui by search.uiState.collectAsState()
    val results by search.results.collectAsState()
    val tiers by search.tiers.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    var choosingJuz by remember { mutableStateOf(false) }
    var choosingSurah by remember { mutableStateOf(false) }
    val heldLabel = stringResource(R.string.group_in_cd)

    BackHandler(onBack = onBack)
    // Opened to search: the box takes the typing at once, unless a search is still there from before.
    LaunchedEffect(Unit) {
        if (search.uiState.value.query.isBlank()) {
            focus.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(list.isScrollInProgress) { if (list.isScrollInProgress) keyboard?.hide() }
    LaunchedEffect(results.query, ui.scope) { list.scrollToItem(0) }

    // Ticking puts the keyboard away, so the bar that adds the ticked verses shows.
    fun pick(id: Int) = Pick(chosen = id in ui.chosen, held = id in inGroup, heldLabel = heldLabel) {
        keyboard?.hide()
        search.toggleChosen(id)
    }

    Scaffold(
        topBar = { PaperHeader(title = stringResource(R.string.group_add_verses), compact = true, onBack = onBack) },
        bottomBar = {
            if (ui.chosen.isNotEmpty()) {
                AddChosenBar(
                    count = ui.chosen.size,
                    onAdd = {
                        keyboard?.hide()
                        val picked = ui.chosen.sorted()
                        search.clearChosen()
                        onAdd(picked)
                    },
                    onClear = search::clearChosen
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            SearchBox(
                value = ui.query,
                onValueChange = search::setQuery,
                hint = stringResource(R.string.group_add_search_hint),
                focus = focus
            )
            ScopeChips(
                scope = ui.scope,
                hasHifz = ui.hasHifz,
                onAll = { search.setScope(SearchScope.All) },
                onHifz = { search.setScope(SearchScope.Hifz) },
                onJuz = { keyboard?.hide(); choosingJuz = true },
                onSurah = { keyboard?.hide(); choosingSurah = true }
            )

            // A reference typed in the box (البقرة 255, 2:255) is offered first, as the verse it names.
            val named = results.goTo.filterIsInstance<GoTo.Verse>().mapNotNull { goTo ->
                val (page, id) = search.target(goTo)
                id?.let { SearchHit(it, goTo.surah, goTo.ayah, page, emptyList()) }
            }.distinctBy { it.ayahId }
            val namedIds = named.map { it.ayahId }.toSet()

            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)
            ) {
                if (ui.query.isBlank()) {
                    item(key = "tips") {
                        Text(
                            text = stringResource(R.string.group_add_tips),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 8.dp)
                        )
                    }
                } else {
                    named.forEach { hit ->
                        stickyHeader(key = "named-head-${hit.ayahId}") { SurahHeader(hit.surah, 1) }
                        item(key = "named-${hit.ayahId}") {
                            HitRow(hit = hit, tier = tiers[hit.page], onPeek = {}, onOpenPage = { onOpenMushaf(hit.page) }, pick = pick(hit.ayahId))
                        }
                    }
                    results.groups.forEach { group ->
                        val hits = group.hits.filter { it.ayahId !in namedIds }
                        if (hits.isEmpty()) return@forEach
                        stickyHeader(key = "surah-${group.surah}") { SurahHeader(group.surah, hits.size) }
                        items(hits, key = { "ayah-${it.ayahId}" }) { hit ->
                            HitRow(hit = hit, tier = tiers[hit.page], onPeek = {}, onOpenPage = { onOpenMushaf(hit.page) }, pick = pick(hit.ayahId))
                        }
                    }
                    if (named.isEmpty() && results.total == 0 && results.query == ui.query.trim()) {
                        item(key = "none") {
                            when {
                                results.loading -> Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                                    PaperLoading(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                results.searched -> NoVerses(scoped = ui.scope != SearchScope.All, onWiden = { search.setScope(SearchScope.All) })
                                else -> Text(
                                    text = stringResource(R.string.group_add_tips),
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

    if (choosingJuz) {
        JuzSheet(
            chosen = (ui.scope as? SearchScope.Juz)?.juz.orEmpty(),
            onToggle = search::toggleJuz,
            onClear = { search.setScope(SearchScope.All) },
            onDone = { choosingJuz = false }
        )
    }
    if (choosingSurah) {
        SurahSheet(
            chosen = (ui.scope as? SearchScope.Surah)?.surah,
            onPick = { surah ->
                search.setScope(if (surah == null) SearchScope.All else SearchScope.Surah(surah))
                choosingSurah = false
            },
            onDismiss = { choosingSurah = false }
        )
    }
}
