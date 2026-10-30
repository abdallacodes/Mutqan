package com.example.mutqan.ui.mushaf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.produceState
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

private const val MAX_SHOWN = 4

private class SimilarLine(val surah: Int, val ayah: Int, val page: Int, val words: List<String>, val match: SimilarMatch)

/** The ayahs that most resemble [ayahId], from the bundled similar-ayahs data; nothing when it has none. */
@Composable
fun ColumnScope.SimilarAyahsSection(ayahId: Int, onOpenMushaf: ((Int) -> Unit)?) {
    val context = LocalContext.current
    val lines by produceState<Pair<List<SimilarLine>, Int>?>(null, ayahId) {
        value = withContext(Dispatchers.IO) {
            val all = SimilarLibrary.load(context).similarTo(ayahId)
            val repo = QuranRepository.getInstance(context)
            all.take(MAX_SHOWN).mapNotNull { m ->
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
    shown.forEach { line ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = onOpenMushaf != null) { onOpenMushaf?.invoke(line.page) }
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
            Text(
                text = buildAnnotatedString {
                    line.words.forEachIndexed { i, word ->
                        if (i > 0) append(" ")
                        if (line.match.matchedWords.any { i + 1 in it }) {
                            withStyle(SpanStyle(color = markColor, fontWeight = FontWeight.Bold)) { append(word) }
                        } else {
                            append(word)
                        }
                    }
                },
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDirection = TextDirection.Rtl,
                    fontFamily = AmiriFontFamily,
                    fontSize = 20.sp,
                    lineHeight = 40.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    if (total > shown.size) {
        Text(
            text = stringResource(R.string.peek_similar_more, total - shown.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
