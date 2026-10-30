package com.example.mutqan.ui.vault

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.domain.ReadyGroup
import com.example.mutqan.domain.ReadyPhrase
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.components.EmptyStateCard
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperOutlinedButton
import com.example.mutqan.ui.components.PaperTabs
import com.example.mutqan.ui.theme.AmiriFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The prepared lists: repeated phrases, and groups of similar ayahs in a folder per surah.
 * Open a phrase or group to see its ayahs and add it to the vault.
 */
@Composable
fun ReadyListScreen(
    onBack: () -> Unit,
    onOpenMushaf: (page: Int) -> Unit
) {
    val context = LocalContext.current
    val viewModel: ReadyListViewModel = viewModel(factory = ReadyListViewModelFactory(context))
    val tab by viewModel.tab.collectAsState()
    val phrases by viewModel.phrases.collectAsState()
    val query by viewModel.query.collectAsState()
    val expandedId by viewModel.expandedId.collectAsState()
    val added by viewModel.addedTexts.collectAsState()
    val surahGroups by viewModel.surahGroups.collectAsState()
    val openSurah by viewModel.openSurah.collectAsState()
    val expandedGroupId by viewModel.expandedGroupId.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { Toast.makeText(context, it.asString(context), Toast.LENGTH_SHORT).show() }
    }

    val inFolder = tab == ReadyTab.SIMILAR && openSurah != null
    BackHandler(enabled = inFolder) { viewModel.openSurah(null) }

    Scaffold(
        topBar = {
            PaperHeader(
                title = openSurah?.takeIf { inFolder }?.let { SurahData.nameOf(it) } ?: stringResource(R.string.ready_list_title),
                subtitle = if (inFolder) stringResource(R.string.ready_list_similar_subtitle) else stringResource(R.string.ready_list_subtitle),
                onBack = if (inFolder) { { viewModel.openSurah(null) } } else onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (!inFolder) {
                PaperTabs(
                    labels = listOf(
                        stringResource(R.string.ready_tab_phrases),
                        stringResource(R.string.ready_tab_similar)
                    ),
                    selectedIndex = tab.ordinal,
                    onSelect = { viewModel.setTab(ReadyTab.entries[it]) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            when {
                tab == ReadyTab.PHRASES -> PhrasesList(
                    phrases = phrases,
                    query = query,
                    expandedId = expandedId,
                    added = added,
                    onQuery = viewModel::setQuery,
                    onToggle = viewModel::toggle,
                    onAdd = viewModel::addToVault,
                    onOpenMushaf = onOpenMushaf
                )

                openSurah == null -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(surahGroups.keys.toList(), key = { it }) { surah ->
                        val count = surahGroups.getValue(surah).size
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openSurah(surah) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "$surah",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(top = 4.dp).widthIn(min = 36.dp)
                            )
                            Text(
                                text = SurahData.nameOf(surah),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(R.string.ready_folder_count, count),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        Hairline()
                    }
                }

                else -> {
                    val groups = surahGroups[openSurah].orEmpty()
                    val pending = groups.count { it.description !in added }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                if (pending == 0) {
                                    Text(
                                        text = stringResource(R.string.ready_folder_all_added),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                } else {
                                    PaperOutlinedButton(
                                        text = stringResource(R.string.ready_add_folder, pending),
                                        onClick = { openSurah?.let(viewModel::addSurahFolder) }
                                    )
                                }
                            }
                            Hairline()
                        }
                        items(groups, key = { it.id }) { group ->
                            GroupRow(
                                group = group,
                                expanded = expandedGroupId == group.id,
                                inVault = group.description in added,
                                onToggle = { viewModel.toggleGroup(group) },
                                onAdd = { viewModel.addToVault(group) },
                                onOpenMushaf = onOpenMushaf
                            )
                            Hairline()
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhrasesList(
    phrases: List<ReadyPhrase>,
    query: String,
    expandedId: Int?,
    added: Set<String>,
    onQuery: (String) -> Unit,
    onToggle: (ReadyPhrase) -> Unit,
    onAdd: (ReadyPhrase) -> Unit,
    onOpenMushaf: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.ready_list_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (phrases.isEmpty() && query.isNotBlank()) {
            EmptyStateCard(
                icon = Icons.Default.Search,
                title = stringResource(R.string.ready_list_empty_title),
                description = stringResource(R.string.ready_list_empty_body)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(phrases, key = { it.id }) { phrase ->
                    ReadyItemRow(
                        title = phrase.text,
                        count = phrase.verses.size,
                        expanded = expandedId == phrase.id,
                        inVault = phrase.text in added,
                        onToggle = { onToggle(phrase) },
                        onAdd = { onAdd(phrase) }
                    ) {
                        phrase.verses.forEach { verse ->
                            VerseLine(verse.ayahId, listOf(verse.firstWord..verse.lastWord), onOpenMushaf)
                        }
                    }
                    Hairline()
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun GroupRow(
    group: ReadyGroup,
    expanded: Boolean,
    inVault: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onOpenMushaf: (Int) -> Unit
) {
    ReadyItemRow(
        title = group.description,
        count = group.members.size,
        expanded = expanded,
        inVault = inVault,
        onToggle = onToggle,
        onAdd = onAdd
    ) {
        group.members.forEach { VerseLine(it.ayahId, it.matchedWords, onOpenMushaf) }
    }
}

/** A phrase or group as a line: its title and size; opened, the add button and its ayahs. */
@Composable
private fun ReadyItemRow(
    title: String,
    count: Int,
    expanded: Boolean,
    inVault: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    verses: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = AmiriFontFamily, fontSize = 22.sp, lineHeight = 38.sp),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.ready_list_verses_count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        if (expanded) {
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (inVault) {
                    Text(
                        text = stringResource(R.string.ready_list_added),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    PaperOutlinedButton(text = stringResource(R.string.ready_list_add), onClick = onAdd)
                }
            }
            Spacer(Modifier.height(8.dp))
            verses()
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** One ayah, with the given word ranges (1-based, inclusive) marked; tapping opens it in the Mushaf. */
@Composable
private fun VerseLine(ayahId: Int, marked: List<IntRange>, onOpenMushaf: (Int) -> Unit) {
    val context = LocalContext.current
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val markColor = MaterialTheme.colorScheme.primary
    val loaded by produceState<VerseView?>(null, ayahId) {
        value = withContext(Dispatchers.IO) {
            val repo = QuranRepository.getInstance(context)
            val ayah = repo.ayahById(ayahId) ?: return@withContext null
            val surah = repo.surah(ayah.surah)
            VerseView(
                surahName = surah?.let { if (isArabic) it.nameArabic else it.nameSimple }.orEmpty(),
                ayah = ayah.ayah,
                page = ayah.firstPage,
                words = repo.wordsOfAyah(ayah.surah, ayah.ayah).filter { !it.isEnd }.map { it.hafs }
            )
        }
    }
    val view = loaded ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenMushaf(view.page) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "${view.surahName} ${view.ayah}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary
        )
        Text(
            text = buildAnnotatedString {
                view.words.forEachIndexed { i, word ->
                    if (i > 0) append(" ")
                    if (marked.any { i + 1 in it }) {
                        withStyle(SpanStyle(color = markColor, fontWeight = FontWeight.Bold)) { append(word) }
                    } else {
                        append(word)
                    }
                }
            },
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = AmiriFontFamily, fontSize = 20.sp, lineHeight = 36.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private class VerseView(val surahName: String, val ayah: Int, val page: Int, val words: List<String>)
