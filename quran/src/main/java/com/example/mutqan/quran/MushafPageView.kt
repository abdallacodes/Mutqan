package com.example.mutqan.quran

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerInputChange
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Covers the words of a page for reciting from memory: every ayah is drawn as grey bars in place of its words
 * unless its id (`QuranAyah.id`) is in [revealedAyahs].
 */
class PageCover(val revealedAyahs: Set<Int>) {
    fun hides(ayahId: Int) = ayahId != 0 && ayahId !in revealedAyahs
}

private sealed interface PageLoad {
    data object Loading : PageLoad
    data class Ready(val data: MushafPageData, val typeface: Typeface) : PageLoad
    data object Failed : PageLoad
}

/**
 * One Mushaf page drawn from the QPC V2 layout and that page's own font.
 * Fetches the font pack on first use; shows a spinner meanwhile and a retry button if
 * the download fails.
 *
 * [highlights] maps a global ayah id (`QuranAyah.id`) to the colour painted behind its words.
 * [onWordTap] receives the word id and ayah id of a tapped word and returns true if it handled
 * the tap; a tap it does not handle (or one that misses every word) is left for parent
 * gestures such as toggling the chrome. [onWordLongPress] is the same for a press held on a
 * word, which is never mistaken for a tap.
 * [cover], when given, hides the words of every ayah not in its revealed set. [quarterStarts] are the ids of the
 * words (`QuranWord.id`) that open a hizb quarter; each gets a ۞ in the right margin.
 */
@Composable
fun MushafPageView(
    page:    Int,
    quran:   QuranRepository,
    fonts:   MushafFontManager,
    ink:     Color,
    modifier: Modifier = Modifier,
    highlights: Map<Int, Color> = emptyMap(),
    onWordTap: ((wordId: Int, ayahId: Int) -> Boolean)? = null,
    onWordLongPress: ((wordId: Int, ayahId: Int) -> Boolean)? = null,
    cover: PageCover? = null,
    quarterStarts: Set<Int> = emptySet()
) {
    var attempt by remember(page) { mutableIntStateOf(0) }

    val load by produceState<PageLoad>(PageLoad.Loading, page, attempt) {
        value = PageLoad.Loading
        value = try {
            withContext(Dispatchers.IO) {
                val data = quran.page(page)
                val typeface = if (fonts.ensure(page)) fonts.typeface(page) else null
                if (typeface != null) PageLoad.Ready(data, typeface) else PageLoad.Failed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PageLoad.Failed
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (val state = load) {
            PageLoad.Loading -> CircularProgressIndicator(
                modifier    = Modifier.size(48.dp),
                color       = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            PageLoad.Failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.padding(24.dp)
            ) {
                Text(
                    text      = stringResource(R.string.mushaf_font_failed),
                    color     = ink,
                    textAlign = TextAlign.Center,
                    style     = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = { attempt++ }) {
                    Text(stringResource(R.string.mushaf_retry))
                }
            }
            is PageLoad.Ready -> PageCanvas(state, ink, highlights, onWordTap, onWordLongPress, cover, quarterStarts)
        }
    }
}

@Composable
private fun PageCanvas(
    ready: PageLoad.Ready,
    ink: Color,
    highlights: Map<Int, Color>,
    onWordTap: ((wordId: Int, ayahId: Int) -> Boolean)?,
    onWordLongPress: ((wordId: Int, ayahId: Int) -> Boolean)?,
    cover: PageCover?,
    quarterStarts: Set<Int>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val headerTypeface = remember(context) { ResourcesCompat.getFont(context, R.font.amiri_regular) }
    val basmallah = stringResource(R.string.mushaf_basmallah)

    val pagePaint = remember(ready.typeface) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = ready.typeface }
    }
    val headerPaint = remember(headerTypeface) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface  = headerTypeface
            textAlign = Paint.Align.CENTER
        }
    }

    var size by remember { mutableStateOf(IntSize.Zero) }
    val geometry = remember(ready, size) {
        if (size == IntSize.Zero) null
        else {
            val measure = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = ready.typeface
                textSize = MushafPageGeometry.MEASURE_SIZE
            }
            val space = measure.measureText(" ").takeIf { it > 0f } ?: (MushafPageGeometry.MEASURE_SIZE * 0.25f)
            MushafPageGeometry.compute(
                page                 = ready.data,
                width                = size.width.toFloat(),
                height               = size.height.toFloat(),
                advanceAtMeasureSize = { measure.measureText(it) },
                spaceAtMeasureSize   = space
            )
        }
    }

    val currentOnWordTap by rememberUpdatedState(onWordTap)
    val currentOnWordLongPress by rememberUpdatedState(onWordLongPress)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis

    Canvas(
        modifier = modifier
            .aspectRatio(MushafPageGeometry.PAGE_ASPECT)
            .onSizeChanged { size = it }
            .pointerInput(geometry, onWordTap != null, onWordLongPress != null) {
                if (onWordTap == null && onWordLongPress == null) return@pointerInput
                val g = geometry ?: return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var up: PointerInputChange? = null
                    val released = withTimeoutOrNull(longPressTimeout) { up = waitForUpOrCancellation() }
                    if (released == null) {
                        // Still held after the long-press timeout (a drag or scroll would have ended the wait).
                        val word = MushafPageGeometry.hitTest(g, down.position.x, down.position.y)
                        if (word != null && word.ayahId != 0 && currentOnWordLongPress?.invoke(word.wordId, word.ayahId) == true) {
                            waitForUpOrCancellation()?.consume()
                        }
                        return@awaitEachGesture
                    }
                    val release = up ?: return@awaitEachGesture
                    if (currentOnWordTap == null) return@awaitEachGesture
                    if ((release.position - down.position).getDistance() > touchSlop) return@awaitEachGesture
                    val word = MushafPageGeometry.hitTest(g, release.position.x, release.position.y)
                    if (word != null && word.ayahId != 0 && currentOnWordTap?.invoke(word.wordId, word.ayahId) == true) {
                        release.consume()
                    }
                }
            }
    ) {
        val g = geometry ?: return@Canvas
        val inkArgb = ink.toArgb()
        pagePaint.color = inkArgb
        pagePaint.textSize = g.textSize
        headerPaint.color = inkArgb
        headerPaint.textSize = g.lineHeight * 0.45f

        val pageBaseline = -(pagePaint.fontMetrics.ascent + pagePaint.fontMetrics.descent) / 2f
        val headerBaseline = -(headerPaint.fontMetrics.ascent + headerPaint.fontMetrics.descent) / 2f
        val margin = this.size.width * 0.045f

        if (highlights.isNotEmpty()) {
            val pad = g.textSize * 0.12f
            for (run in MushafPageGeometry.ayahRuns(g, highlights.keys)) {
                val color = highlights[run.ayahId] ?: continue
                drawRoundRect(
                    color        = color,
                    topLeft      = Offset(run.left - pad, run.centerY - g.lineHeight * 0.46f),
                    size         = Size(run.right - run.left + 2 * pad, g.lineHeight * 0.92f),
                    cornerRadius = CornerRadius(g.lineHeight * 0.12f)
                )
            }
        }

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            for (placed in g.lines) {
                when (placed.line.type) {
                    MushafLineType.AYAH ->
                        for (word in placed.words) {
                            if (cover?.hides(word.ayahId) != true) {
                                native.drawText(word.glyph, word.x, placed.centerY + pageBaseline, pagePaint)
                            }
                            if (word.wordId in quarterStarts) {
                                native.drawText("۞", this.size.width - margin / 2f, placed.centerY + headerBaseline, headerPaint)
                            }
                        }
                    MushafLineType.SURAH_NAME -> {
                        val name = placed.line.surahNameArabic.orEmpty()
                        native.drawText(
                            context.getString(R.string.mushaf_surah_title, name),
                            this.size.width / 2f, placed.centerY + headerBaseline, headerPaint
                        )
                    }
                    MushafLineType.BASMALLAH ->
                        native.drawText(basmallah, this.size.width / 2f, placed.centerY + headerBaseline, headerPaint)
                }
            }
        }

        if (cover != null) {
            val bar = ink.copy(alpha = 0.16f)
            for (placed in g.lines) {
                for (word in placed.words) {
                    if (!cover.hides(word.ayahId)) continue
                    drawRoundRect(
                        color        = bar,
                        topLeft      = Offset(word.x, placed.centerY - g.lineHeight * 0.17f),
                        size         = Size(word.width, g.lineHeight * 0.34f),
                        cornerRadius = CornerRadius(g.lineHeight * 0.1f)
                    )
                }
            }
        }

        for (placed in g.lines) {
            if (placed.line.type != MushafLineType.SURAH_NAME) continue
            val inset = g.lineHeight * 0.1f
            drawRoundRect(
                color        = ink.copy(alpha = 0.7f),
                topLeft      = Offset(margin, placed.centerY - g.lineHeight / 2f + inset),
                size         = Size(this.size.width - 2 * margin, g.lineHeight - 2 * inset),
                cornerRadius = CornerRadius(g.lineHeight * 0.12f),
                style        = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
