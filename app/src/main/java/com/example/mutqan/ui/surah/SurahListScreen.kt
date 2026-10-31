package com.example.mutqan.ui.surah

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.quran.ArabicNormalization
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.HubRow
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.PaperTabs
import com.example.mutqan.ui.components.TopBarOverflowMenu

/**
 * The Quran tab: a way into the Mushaf. Tapping a surah or a juz opens it to read; the list says which
 * surahs are memorised, a link continues where the last reading stopped, and the search field takes a
 * surah name, a surah number or a page number.
 */
@Composable
fun SurahListScreen(
    onSurahClick: (Int) -> Unit,
    onOpenMushaf: (page: Int) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onOpenRoot: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: SurahListViewModel = viewModel(factory = SurahListViewModelFactory(context))
    val surahs by viewModel.surahs.collectAsState()
    val juz by viewModel.juz.collectAsState()
    val lastPage by viewModel.lastPage.collectAsState()
    val morphology by viewModel.morphology.collectAsState()

    var showHelpDialog by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    val needle = ArabicNormalization.normalizeForSearch(query.trim())
    val page = query.trim().toIntOrNull()?.takeIf { it in 1..604 }
    // Typing the letters of a root (2 to 4 letters) offers that root as well as any surah.
    val roots = remember(morphology, query) {
        val m = morphology
        if (m == null || query.isBlank() || query.trim().toIntOrNull() != null) emptyList()
        else m.findRoots(query).take(3).map { it to m.infoOfRoot(it) }
    }
    val shown = remember(surahs, needle) {
        if (needle.isEmpty()) surahs
        else surahs.filter { item ->
            item.info.id.toString() == needle ||
                ArabicNormalization.normalizeForSearch(item.info.nameArabic).contains(needle) ||
                item.info.nameEnglish.contains(query.trim(), ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PaperHeader(
            title = stringResource(R.string.nav_quran),
            subtitle = stringResource(R.string.surahs_subtitle),
            actions = {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.nav_search))
                }
                TopBarOverflowMenu(onHelpClick = { showHelpDialog = true }, onSettingsClick = onSettingsClick)
            }
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            lastPage?.let { last ->
                item {
                    HubRow(
                        title = stringResource(R.string.home_continue_title),
                        subtitle = stringResource(R.string.home_continue_page, last),
                        onClick = { onOpenMushaf(last) }
                    )
                }
            }

            item {
                PaperTabs(
                    labels = listOf(stringResource(R.string.quran_tab_surahs), stringResource(R.string.quran_tab_juz)),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 4.dp)
                )
            }

            if (tab == 0) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.quran_search_hint)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 8.dp)
                    )
                }
                items(roots, key = { "root-${it.first}" }) { (root, info) ->
                    HubRow(
                        title = stringResource(R.string.root_row_title, morphology!!.rootSpaced(root)),
                        subtitle = stringResource(R.string.root_summary, info.words, info.verses),
                        onClick = { onOpenRoot(root) }
                    )
                }
                if (page != null) {
                    item {
                        HubRow(
                            title = stringResource(R.string.quran_go_page, page),
                            subtitle = null,
                            onClick = { onOpenMushaf(page) }
                        )
                    }
                }
                items(shown, key = { it.info.id }) { item ->
                    SurahRow(
                        item = item,
                        onClick = { if (item.startPage > 0) onOpenMushaf(item.startPage) },
                        onOpenGroups = { onSurahClick(item.info.id) }
                    )
                }
            } else {
                items(juz, key = { it.juz }) { entry ->
                    JuzRow(entry, onClick = { onOpenMushaf(entry.startPage) })
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showHelpDialog) {
        com.example.mutqan.ui.components.HelpDialog(
            title = stringResource(R.string.help_surahs_title),
            description = stringResource(R.string.help_surahs_desc),
            onDismiss = { showHelpDialog = false }
        )
    }
}

/** A surah as a line: the line opens it in the Mushaf; the look-alikes count, when there is one, opens its groups. */
@Composable
private fun SurahRow(item: SurahListItem, onClick: () -> Unit, onOpenGroups: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = PaperSpace.Gutter, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "${item.info.id}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.width(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.info.getDisplayName(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.verseCount > 0) {
                    Text(
                        text = stringResource(R.string.quran_surah_info, item.verseCount, item.startJuz, item.startPage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                when (item.memorised) {
                    SurahMemorised.FULL -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.quran_memorised),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    SurahMemorised.PARTLY -> Text(
                        text = stringResource(R.string.coverage_surah_partly),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    SurahMemorised.NONE -> {}
                }
                if (item.groupCount > 0) {
                    Text(
                        text = stringResource(R.string.quran_lookalikes, item.groupCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(role = Role.Button, onClick = onOpenGroups)
                            .padding(top = 4.dp, bottom = 4.dp, start = 8.dp)
                    )
                }
            }
        }
        Hairline()
    }
}

@Composable
private fun JuzRow(entry: JuzListItem, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = PaperSpace.Gutter, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.plan_item_juz, entry.juz),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.quran_juz_info, entry.startPage, SurahData.nameOf(entry.firstSurah)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Hairline()
    }
}
