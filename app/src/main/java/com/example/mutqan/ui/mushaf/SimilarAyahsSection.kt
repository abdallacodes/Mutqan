package com.example.mutqan.ui.mushaf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.example.mutqan.domain.SimilarLibrary
import com.example.mutqan.domain.SimilarMatch
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.ui.theme.AmiriFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.mutqan.ui.components.AyahText
import com.example.mutqan.ui.components.Hairline
import com.example.mutqan.ui.components.PaperLink

/** Ten at a time keeps the sheet readable; the rest are a tap away. */
private const val PAGE_SIZE = 10

private class SimilarLine(val surah: Int, val ayah: Int, val page: Int, val words: List<String>, val match: SimilarMatch)

/** The ayahs that most resemble [ayahId], best first and [PAGE_SIZE] at a time, from the bundled similar-ayahs data; nothing when it has none. */
@Composable
fun ColumnScope.SimilarAyahsSection(ayahId: Int, onOpenMushaf: ((Int) -> Unit)?) {
    val context = LocalContext.current
    var page by remember(ayahId) { mutableIntStateOf(0) }
    val lines by produceState<Pair<List<SimilarLine>, Int>?>(null, ayahId, page) {
        value = withContext(Dispatchers.IO) {
            val all = SimilarLibrary.load(context).similarTo(ayahId)
            val repo = QuranRepository.getInstance(context)
            all.drop(page * PAGE_SIZE).take(PAGE_SIZE).mapNotNull { m ->
                val ayah = repo.ayahById(m.ayahId) ?: return@mapNotNull null
                SimilarLine(
                    ayah.surah, ayah.ayah, ayah.firstPage,
                    repo.wordsOfAyah(ayah.surah, ayah.ayah).filter { !it.isEnd }.map { it.hafs }, m
                )
            } to all.size
        }
    }
    val (shown, total) = lines ?: return
    if (shown.isEmpty()) return

    val markColor = MaterialTheme.colorScheme.primary
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(top = 16.dp))
    Text(
        text = stringResource(R.string.peek_similar_title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp)
    )
    shown.forEachIndexed { index, line ->
        if (index > 0) Hairline(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = onOpenMushaf != null) { onOpenMushaf?.invoke(line.page) }
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(R.string.peek_group_ayah_ref, SurahData.nameOf(line.surah), line.ayah),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.peek_similar_score, line.match.score),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            AyahText(ayahId = line.match.ayahId, size = 22.sp, marked = line.match.matchedWords, markColor = markColor)
        }
    }
    if (total > PAGE_SIZE) {
        val first = page * PAGE_SIZE + 1
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (page > 0) PaperLink(stringResource(R.string.peek_similar_prev), onClick = { page-- })
            }
            Text(
                text = stringResource(R.string.peek_similar_range, first, first + shown.size - 1, total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (first + PAGE_SIZE <= total) PaperLink(stringResource(R.string.peek_similar_next), onClick = { page++ })
            }
        }
    }
}
