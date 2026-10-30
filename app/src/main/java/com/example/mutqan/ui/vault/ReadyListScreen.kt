package com.example.mutqan.ui.vault

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
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
import com.example.mutqan.domain.PhraseVerse
import com.example.mutqan.domain.ReadyPhrase
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.components.EmptyStateCard
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperOutlinedButton
import com.example.mutqan.ui.theme.AmiriFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Browse the bundled list of repeated phrases; open one to see its ayahs and add it to the vault. */
@Composable
fun ReadyListScreen(
    onBack: () -> Unit,
    onOpenMushaf: (page: Int) -> Unit
) {
    val context = LocalContext.current
    val viewModel: ReadyListViewModel = viewModel(factory = ReadyListViewModelFactory(context))
    val phrases by viewModel.phrases.collectAsState()
    val query by viewModel.query.collectAsState()
    val expandedId by viewModel.expandedId.collectAsState()
    val added by viewModel.addedTexts.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { Toast.makeText(context, it.asString(context), Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        topBar = {
            PaperHeader(
                title = stringResource(R.string.ready_list_title),
                subtitle = stringResource(R.string.ready_list_subtitle),
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
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
                        PhraseRow(
                            phrase = phrase,
                            expanded = expandedId == phrase.id,
                            inVault = phrase.text in added,
                            onToggle = { viewModel.toggle(phrase) },
                            onAdd = { viewModel.addToVault(phrase) },
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

@Composable
private fun PhraseRow(
    phrase: ReadyPhrase,
    expanded: Boolean,
    inVault: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onOpenMushaf: (Int) -> Unit
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
                text = phrase.text,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = AmiriFontFamily, fontSize = 22.sp, lineHeight = 38.sp),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.ready_list_verses_count, phrase.verses.size),
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
            phrase.verses.forEach { verse ->
                VerseLine(verse = verse, onOpenMushaf = onOpenMushaf)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** One ayah that contains the phrase, with the phrase's words marked. */
@Composable
private fun VerseLine(verse: PhraseVerse, onOpenMushaf: (Int) -> Unit) {
    val context = LocalContext.current
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val marked = MaterialTheme.colorScheme.primary
    val loaded by produceState<VerseView?>(null, verse) {
        value = withContext(Dispatchers.IO) {
            val repo = QuranRepository.getInstance(context)
            val ayah = repo.ayahById(verse.ayahId) ?: return@withContext null
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
                    if (i + 1 in verse.firstWord..verse.lastWord) {
                        withStyle(SpanStyle(color = marked, fontWeight = FontWeight.Bold)) { append(word) }
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
