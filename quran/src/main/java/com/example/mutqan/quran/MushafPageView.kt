package com.example.mutqan.quran

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
 * gestures such as toggling the chrome.
 */
@Composable
fun MushafPageView(
    page:    Int,
    quran:   QuranRepository,
    fonts:   MushafFontManager,
    ink:     Color,
    modifier: Modifier = Modifier,
    highlights: Map<Int, Color> = emptyMap(),
    onWordTap: ((wordId: Int, ayahId: Int) -> Boolean)? = null
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
            is PageLoad.Ready -> PageCanvas(state, ink, highlights, onWordTap)
        }
    }
}

@Composable
private fun PageCanvas(
    ready: PageLoad.Ready,
    ink: Color,
    highlights: Map<Int, Color>,
    onWordTap: ((wordId: Int, ayahId: Int) -> Boolean)?,
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
    val touchSlop = LocalViewConfiguration.current.touchSlop

    Canvas(
        modifier = modifier
            .aspectRatio(MushafPageGeometry.PAGE_ASPECT)
            .onSizeChanged { size = it }
            .pointerInput(geometry, onWordTap != null) {
                if (onWordTap == null) return@pointerInput
                val g = geometry ?: return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                    if ((up.position - down.position).getDistance() > touchSlop) return@awaitEachGesture
                    val word = MushafPageGeometry.hitTest(g, up.position.x, up.position.y)
                    if (word != null && word.ayahId != 0 && currentOnWordTap?.invoke(word.wordId, word.ayahId) == true) {
                        up.consume()
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
                            native.drawText(word.glyph, word.x, placed.centerY + pageBaseline, pagePaint)
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
