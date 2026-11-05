package com.example.mutqan.ui.components

import android.util.LruCache
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.mutqan.R
import com.example.mutqan.quran.MushafFontManager
import com.example.mutqan.quran.MushafSpan
import com.example.mutqan.quran.QuranRepository
import com.example.mutqan.quran.QuranWord
import com.example.mutqan.quran.SpanPiece
import com.example.mutqan.quran.toCleanQuranicText
import com.example.mutqan.ui.theme.AmiriFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Quran text drawn the way the Mushaf prints it: every word in the font of the page it is printed on.
 *
 * Those fonts are fetched on demand, so until the ones this text needs are on the device it is shown as ordinary text
 * in Amiri, and a download of what is missing starts in the background; when it finishes the text is redrawn.
 * [wordColors] maps a word id (`QuranWord.id`) to the colour of that word.
 */
@Composable
fun MushafText(
    span: MushafSpan,
    modifier: Modifier = Modifier.fillMaxWidth(),
    size: TextUnit = 26.sp,
    color: Color = MaterialTheme.colorScheme.onBackground,
    maxLines: Int = Int.MAX_VALUE,
    wordColors: Map<Int, Color> = emptyMap(),
    textAlign: TextAlign = TextAlign.Start
) {
    val context = LocalContext.current
    val fonts = remember(context) { MushafFontManager.getInstance(context) }
    val revision by fonts.revision.collectAsState()
    val pages = span.pages

    val faces = remember(pages, revision) {
        fonts.cached(pages) ?: if (pages.all(fonts::isAvailable)) fonts.load(pages) else null
    }
    LaunchedEffect(pages, faces == null) {
        if (faces == null) fonts.request(pages)
    }

    val text = remember(span, faces, wordColors) {
        buildAnnotatedString {
            var first = true
            for (piece in span.pieces) {
                when (piece) {
                    is SpanPiece.Word -> {
                        val face = faces?.get(piece.word.page)
                        val style = SpanStyle(
                            fontFamily = face?.let { FontFamily(it) },
                            color = wordColors[piece.word.id] ?: Color.Unspecified
                        )
                        val shown = if (face != null) piece.word.glyph else piece.word.hafs.toPlainQuran()
                        if (shown.isEmpty()) continue
                        if (!first) append(' ')
                        withStyle(style) { append(shown) }
                    }
                    is SpanPiece.Mark -> {
                        if (!first) append(' ')
                        append(piece.text)
                    }
                }
                first = false
            }
        }
    }

    // The page fonts draw each word from a private glyph code; a screen reader gets the words themselves.
    val blank = stringResource(R.string.cd_verse_blank)
    val spoken = remember(span, blank) {
        span.pieces.joinToString(" ") { piece ->
            when (piece) {
                is SpanPiece.Word -> piece.word.hafs.toPlainQuran()
                is SpanPiece.Mark -> if (piece.text == MushafSpan.BLANK) blank else piece.text
            }
        }
    }

    Text(
        text = text,
        modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(spoken) },
        color = color,
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        style = TextStyle(
            fontFamily = AmiriFontFamily,
            fontSize = size,
            lineHeight = size * LINE_HEIGHT,
            textDirection = TextDirection.Rtl,
            textAlign = textAlign
        )
    )
}

private const val LINE_HEIGHT = 1.85f

/** A word's Hafs text without the stop signs the app leaves out of Quran text. */
private fun String.toPlainQuran(): String = toCleanQuranicText().trim()

/**
 * The words of an ayah as shown by [AyahText], with a small memory of the ones already read so that a list scrolled
 * back and forth does not read the database again.
 */
object AyahWords {
    private val cache = LruCache<Int, List<QuranWord>>(400)

    /** The words of ayah [id] (global id) without its end marker, or null if not read yet. */
    fun cached(id: Int): List<QuranWord>? = cache.get(id)

    /** Reads the words of ayah [id] from the database; call off the main thread. */
    fun read(quran: QuranRepository, id: Int): List<QuranWord> {
        cache.get(id)?.let { return it }
        val index = quran.ayahIndex
        if (id !in index) return emptyList()
        return quran.wordsOfAyah(index.surahOf(id), index.ayahOf(id)).filter { !it.isEnd }.also { cache.put(id, it) }
    }

    /** The first [limit] words (all when 0) of [words] as a span, with an ellipsis when some were left out. */
    fun span(words: List<QuranWord>, limit: Int = 0): MushafSpan =
        if (limit in 1 until words.size) MushafSpan.of(words.take(limit)) + MushafSpan.More else MushafSpan.of(words)
}

/**
 * One ayah, by global id (`QuranAyah.id`), drawn like the Mushaf. [firstWords] shows only that many words, then an
 * ellipsis; 0 shows the whole ayah.
 */
@Composable
fun AyahText(
    ayahId: Int,
    modifier: Modifier = Modifier.fillMaxWidth(),
    size: TextUnit = 26.sp,
    color: Color = MaterialTheme.colorScheme.onBackground,
    maxLines: Int = Int.MAX_VALUE,
    firstWords: Int = 0,
    wordColors: Map<Int, Color> = emptyMap(),
    textAlign: TextAlign = TextAlign.Start,
    /** Word positions (1-based, as in a look-alike match) drawn in [markColor]. */
    marked: List<IntRange> = emptyList(),
    markColor: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current
    val words by produceState(AyahWords.cached(ayahId), ayahId) {
        value = withContext(Dispatchers.IO) { AyahWords.read(QuranRepository.getInstance(context), ayahId) }
    }
    val shown = words ?: return
    val colors = remember(shown, marked, markColor, wordColors) {
        if (marked.isEmpty()) wordColors
        else wordColors + shown.filter { w -> marked.any { w.position in it } }.associate { it.id to markColor }
    }
    MushafText(
        span = remember(shown, firstWords) { AyahWords.span(shown, firstWords) },
        modifier = modifier,
        size = size,
        color = color,
        maxLines = maxLines,
        wordColors = colors,
        textAlign = textAlign
    )
}
