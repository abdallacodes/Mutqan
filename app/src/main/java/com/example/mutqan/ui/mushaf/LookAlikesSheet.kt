package com.example.mutqan.ui.mushaf

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mutqan.R
import com.example.mutqan.data.SurahData
import com.example.mutqan.domain.AyahGroup
import com.example.mutqan.domain.MutashabihatLibrary
import com.example.mutqan.domain.PhraseIndex
import com.example.mutqan.domain.SharedPhrase
import com.example.mutqan.domain.SimilarLibrary
import com.example.mutqan.quran.PaperLoading
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.components.AyahText
import com.example.mutqan.ui.components.FolioSheetInsets
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperLink
import com.example.mutqan.ui.components.PaperSpace
import com.example.mutqan.ui.components.PaperTabs
import com.example.mutqan.ui.components.QuickPeekTarget
import com.example.mutqan.ui.components.SectionLabel
import com.example.mutqan.ui.theme.AmiriFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A verse to go to: where it is, a note beside its name (how alike, or the group it is in), and its words to mark. */
private class AlikeLine(val ayahId: Int, val surah: Int, val ayah: Int, val page: Int, val note: String?, val marked: List<IntRange>)

/** One phrase the verse shares, with the other verses that hold it. */
private class PhraseLines(val phrase: SharedPhrase, val lines: List<AlikeLine>)

/** What the sheet shows for a verse: the verses like it (the reader's groups, then the ready list) and its shared phrases. */
private class LookAlikes(val mine: List<AlikeLine>, val ready: List<AlikeLine>, val phrases: List<PhraseLines>) {
    val alikeCount: Int get() = mine.size + ready.size
}

/** The phrase index, built once a run (a few hundred phrases). */
private object Phrases {
    @Volatile
    private var index: PhraseIndex? = null

    suspend fun get(context: Context): PhraseIndex = index ?: PhraseIndex(MutashabihatLibrary.phrases(context)).also { index = it }
}

private suspend fun readLookAlikes(context: Context, ayahId: Int, groups: List<AyahGroup>, scoreNote: (Int) -> String): LookAlikes =
    withContext(Dispatchers.IO) {
        val quran = QuranRepository.getInstance(context)
        val index = quran.ayahIndex
        fun line(id: Int, note: String?, marked: List<IntRange>): AlikeLine? =
            if (id !in index || id == ayahId) null
            else AlikeLine(id, index.surahOf(id), index.ayahOf(id), index.firstPageOf(id), note, marked)
        val mine = groups.flatMap { g -> g.others.mapNotNull { line(it.id, g.group.description, emptyList()) } }.distinctBy { it.ayahId }
        val taken = mine.mapTo(HashSet()) { it.ayahId }
        val ready = SimilarLibrary.load(context).similarTo(ayahId)
            .filter { it.ayahId !in taken }
            .mapNotNull { line(it.ayahId, scoreNote(it.score), it.matchedWords) }
            .distinctBy { it.ayahId }
        val phrases = Phrases.get(context).phrasesOf(ayahId).map { p ->
            PhraseLines(p, p.others.mapNotNull { line(it.ayahId, null, listOf(it.firstWord..it.lastWord)) })
        }.filter { it.lines.isNotEmpty() }
        LookAlikes(mine, ready, phrases)
    }

/** Verses of one phrase shown at first; the rest come with a tap. */
private const val PHRASE_FIRST = 4

/**
 * The verses like [target], on two tabs: the verses that resemble it (from the reader's own groups, then the ready
 * list of similar verses, with how alike and the words they share), and the phrases it shares with other verses (each
 * phrase with the verses that hold it, the phrase marked in each). A tap on a verse goes to it in the Mushaf ([onGo]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookAlikesSheet(
    target: QuickPeekTarget,
    groups: List<AyahGroup>,
    onGo: (ayahId: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scoreFormat = stringResource(R.string.peek_similar_score)
    val found by produceState<LookAlikes?>(null, target.verseId, groups) {
        value = readLookAlikes(context, target.verseId, groups) { scoreFormat.format(it) }
    }
    // Until chosen, the first tab with something on it.
    var chosenTab by rememberSaveable(target.verseId) { mutableIntStateOf(-1) }
    var opened by remember(target.verseId) { mutableStateOf(emptySet<Int>()) }

    ModalBottomSheet(
        contentWindowInsets = FolioSheetInsets,
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).navigationBarsPadding()) {
            Column(modifier = Modifier.padding(horizontal = PaperSpace.Gutter)) {
                Text(
                    text = stringResource(R.string.look_alikes_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.saved_ref, SurahData.nameOf(target.surahId), target.ayahNumber),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            val f = found
            if (f == null) {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    PaperLoading(color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }
            val tab = if (chosenTab >= 0) chosenTab else if (f.alikeCount == 0 && f.phrases.isNotEmpty()) 1 else 0
            PaperTabs(
                labels = listOf(
                    stringResource(R.string.look_alikes_tab_alike, f.alikeCount),
                    stringResource(R.string.look_alikes_tab_phrases, f.phrases.size)
                ),
                selectedIndex = tab,
                onSelect = { chosenTab = it },
                modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 12.dp)
            )
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (tab == 0) {
                    if (f.alikeCount == 0) item(key = "none") { Empty(stringResource(R.string.look_alikes_none_alike)) }
                    if (f.mine.isNotEmpty()) {
                        item(key = "mine") { Label(stringResource(R.string.look_alikes_mine)) }
                        items(f.mine, key = { "m${it.ayahId}" }) { AlikeRow(it, onGo) }
                    }
                    if (f.ready.isNotEmpty()) {
                        if (f.mine.isNotEmpty()) item(key = "ready") { Label(stringResource(R.string.look_alikes_ready)) }
                        items(f.ready, key = { "r${it.ayahId}" }) { AlikeRow(it, onGo) }
                    }
                } else {
                    if (f.phrases.isEmpty()) item(key = "none") { Empty(stringResource(R.string.look_alikes_none_phrases)) }
                    f.phrases.forEach { p ->
                        item(key = "p${p.phrase.id}") { PhraseHead(p.phrase.text, p.lines.size) }
                        val all = p.phrase.id in opened
                        items(if (all) p.lines else p.lines.take(PHRASE_FIRST), key = { "p${p.phrase.id}-${it.ayahId}" }) { AlikeRow(it, onGo) }
                        if (!all && p.lines.size > PHRASE_FIRST) {
                            item(key = "p${p.phrase.id}-more") {
                                PaperLink(
                                    text = stringResource(R.string.look_alikes_show_all, p.lines.size),
                                    onClick = { opened = opened + p.phrase.id },
                                    modifier = Modifier.padding(horizontal = PaperSpace.Gutter - 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    SectionLabel(text, Modifier.padding(horizontal = PaperSpace.Gutter).padding(top = 12.dp, bottom = 2.dp))
}

@Composable
private fun Empty(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = PaperSpace.Gutter, vertical = 16.dp)
    )
}

/** A shared phrase in the Mushaf's script, and how many other verses hold it. */
@Composable
private fun PhraseHead(text: String, count: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter).padding(top = 20.dp, bottom = 2.dp)) {
        Text(
            text = text,
            style = TextStyle(fontFamily = AmiriFontFamily, fontSize = 22.sp, lineHeight = 38.sp, textDirection = TextDirection.Rtl),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = pluralStringResource(R.plurals.look_alikes_phrase_in, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A verse to go to: its name and page, a note (how alike, or the group), and its words with the shared ones marked. */
@Composable
private fun AlikeRow(line: AlikeLine, onGo: (Int) -> Unit) {
    val goLabel = stringResource(R.string.look_alikes_go)
    Column(Modifier.fillMaxWidth().padding(horizontal = PaperSpace.Gutter)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = goLabel) { onGo(line.ayahId) }
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.saved_ref, SurahData.nameOf(line.surah), line.ayah) + " · " + stringResource(R.string.page_n, line.page),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (line.note != null) {
                    Text(
                        text = line.note,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            AyahText(ayahId = line.ayahId, size = 20.sp, marked = line.marked, markColor = MaterialTheme.colorScheme.primary)
        }
        Hairline(Modifier.fillMaxWidth())
    }
}
