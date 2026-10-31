package com.example.mutqan.ui.tafsir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.data.UserPreferencesRepository
import com.example.mutqan.domain.TafsirBlock
import com.example.mutqan.domain.TafsirEntry
import com.example.mutqan.domain.TafsirLibrary
import com.example.mutqan.domain.TafsirSource
import com.example.mutqan.domain.TafsirText
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperChip
import com.example.mutqan.ui.components.PaperHeader
import com.example.mutqan.ui.components.PaperOutlinedButton
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.theme.AmiriFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TOTAL_AYAHS = 6236

private fun TafsirSource.nameRes() = when (this) {
    TafsirSource.SAADI -> R.string.tafsir_saadi
    TafsirSource.MUKHTASAR -> R.string.tafsir_mukhtasar
    TafsirSource.AYSAR -> R.string.tafsir_aysar
    TafsirSource.IRAB -> R.string.tafsir_irab
}

private fun TafsirSource.noteRes() = when (this) {
    TafsirSource.SAADI -> R.string.tafsir_saadi_note
    TafsirSource.MUKHTASAR -> R.string.tafsir_mukhtasar_note
    TafsirSource.AYSAR -> R.string.tafsir_aysar_note
    TafsirSource.IRAB -> R.string.tafsir_irab_note
}

/** The tafsir of one verse, from a source the reader picks; the arrows move to the neighbouring verses. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TafsirScreen(startAyahId: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { UserPreferencesRepository(context.applicationContext) }
    val sourceId by prefs.tafsirSource.collectAsState(initial = TafsirSource.SAADI.id)
    val source = TafsirSource.fromId(sourceId)
    var ayahId by rememberSaveable { mutableIntStateOf(startAyahId.coerceIn(1, TOTAL_AYAHS)) }
    val listState = rememberLazyListState()
    LaunchedEffect(ayahId, sourceId) { listState.scrollToItem(0) }

    val quran = remember { QuranRepository.getInstance(context) }
    val ayah = remember(ayahId) { quran.ayahById(ayahId) }
    val entry by produceState<TafsirEntry?>(null, source, ayahId) {
        value = withContext(Dispatchers.IO) { TafsirLibrary.get(context).entryFor(source, ayahId) }
    }
    val blocks = remember(entry) { entry?.let { TafsirText.parse(it.body) }.orEmpty() }

    Scaffold(
        topBar = {
            PaperHeader(
                title = ayah?.let { stringResource(R.string.saved_ref, SurahData.nameOf(it.surah), it.ayah) } ?: "",
                subtitle = stringResource(R.string.tafsir_title),
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            item {
                ayah?.let {
                    Text(
                        text = it.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDirection = TextDirection.Rtl, fontFamily = AmiriFontFamily, fontSize = 24.sp, lineHeight = 44.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 16.dp)
                    )
                }
                Hairline()
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter, vertical = 8.dp)
                ) {
                    TafsirSource.entries.forEach { s ->
                        PaperChip(
                            selected = s == source,
                            onClick = { scope.launch { prefs.setTafsirSource(s.id) } },
                            label = { Text(stringResource(s.nameRes())) }
                        )
                    }
                }
                Text(
                    text = stringResource(source.noteRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PaperSpace.Gutter)
                )
            }
            val current = entry
            if (current == null || blocks.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.tafsir_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(PaperSpace.Gutter)
                    )
                }
            } else {
                if (current.coversSeveral) {
                    item {
                        val first = quran.ayahById(current.fromId)
                        val last = quran.ayahById(current.toId)
                        if (first != null && last != null) {
                            Text(
                                text = stringResource(R.string.tafsir_covers, first.ayah, last.ayah),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = PaperSpace.Gutter, end = PaperSpace.Gutter, top = 16.dp)
                            )
                        }
                    }
                }
                items(blocks.size) { i -> TafsirParagraph(blocks[i]) }
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(PaperSpace.Gutter)
                ) {
                    PaperOutlinedButton(
                        text = stringResource(R.string.tafsir_previous),
                        onClick = { if (ayahId > 1) ayahId-- },
                        modifier = Modifier.weight(1f)
                    )
                    PaperOutlinedButton(
                        text = stringResource(R.string.tafsir_next),
                        onClick = { if (ayahId < TOTAL_AYAHS) ayahId++ },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TafsirParagraph(block: TafsirBlock) {
    val ink = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.primary
    val text = remember(block, ink, accent) {
        buildAnnotatedString {
            block.runs.forEach { run ->
                if (run.quote) withStyle(SpanStyle(color = accent, fontFamily = AmiriFontFamily, fontSize = 20.sp)) { append(run.text) }
                else append(run.text)
            }
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(
            textDirection = TextDirection.Rtl,
            lineHeight = 30.sp,
            fontWeight = if (block.heading) FontWeight.Bold else FontWeight.Normal
        ),
        color = if (block.heading) accent else ink,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PaperSpace.Gutter, end = PaperSpace.Gutter, top = if (block.heading) 20.dp else 12.dp)
    )
}
