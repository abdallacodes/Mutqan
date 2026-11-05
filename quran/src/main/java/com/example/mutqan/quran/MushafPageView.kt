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
import androidx.compose.ui.graphics.luminance

/**
 * Covers the words of a page for reciting from memory: every ayah is drawn as grey bars in place of its words
 * unless its id (`QuranAyah.id`) is in [revealedAyahs].
 */
class PageCover(val revealedAyahs: Set<Int>) {
    fun hides(ayahId: Int) = ayahId != 0 && ayahId !in revealedAyahs
}

private sealed interface PageLoad {
    data object Loading : PageLoad
    data class Ready(val data: MushafPageData, val typeface: Typeface, val head: MushafHead) : PageLoad
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
                val first = quran.ayahsOnPage(page).firstOrNull()
                val head = MushafHead(first?.let { quran.surah(it.surah)?.nameArabic }.orEmpty(), first?.juz ?: 0, first?.surah ?: 0)
                if (typeface != null) PageLoad.Ready(data, typeface, head) else PageLoad.Failed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PageLoad.Failed
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (val state = load) {
            // Both stand in for the page at its own shape, so a page that is still loading takes the room it will
            // take once drawn. Without that, a page in a scrolling column is a sliver that jumps to full height.
            PageLoad.Loading -> Box(Modifier.aspectRatio(MushafPageGeometry.PAGE_ASPECT), contentAlignment = Alignment.Center) {
                PaperLoading(color = ink)
            }
            PageLoad.Failed -> Box(Modifier.aspectRatio(MushafPageGeometry.PAGE_ASPECT), contentAlignment = Alignment.Center) {
                Column(
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
    val frameTypeface = remember(context) { ResourcesCompat.getFont(context, R.font.quran_common) }
    val nameTypeface = remember(context) { ResourcesCompat.getFont(context, R.font.surah_name_v4) }

    val pagePaint = remember(ready.typeface) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = ready.typeface }
    }
    val railPaint = remember(headerTypeface) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = headerTypeface }
    }
    val headerPaint = remember(headerTypeface) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface  = headerTypeface
            textAlign = Paint.Align.CENTER
        }
    }

    val framePaint = remember(frameTypeface) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = frameTypeface }
    }
    val namePaint = remember(nameTypeface) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = nameTypeface }
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
        val frameColor = surahFrameColor(ink).toArgb()
        pagePaint.color = inkArgb
        pagePaint.textSize = g.textSize
        headerPaint.color = inkArgb
        headerPaint.textSize = g.lineHeight * 0.45f

        val pageBaseline = -(pagePaint.fontMetrics.ascent + pagePaint.fontMetrics.descent) / 2f
        val headerBaseline = -(headerPaint.fontMetrics.ascent + headerPaint.fontMetrics.descent) / 2f
        val margin = this.size.width * 0.045f

        // The running head and foot of a printed page: juz on the right, surah on the left, the page number below.
        val railArgb = ink.copy(alpha = 0.8f).toArgb()
        railPaint.color = railArgb
        railPaint.textSize = g.lineHeight * 0.4f
        val railBaseline = -(railPaint.fontMetrics.ascent + railPaint.fontMetrics.descent) / 2f
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            if (ready.head.juz in 1..JUZ) {
                framePaint.color = railArgb
                drawGlyph(native, framePaint, QuranGlyphs.juzNumber(ready.head.juz), g.lineHeight * RAIL_INK, this.size.width - margin, g.headY, Paint.Align.RIGHT)
            }
            if (ready.head.surahId in 1..SURAHS) {
                namePaint.color = railArgb
                drawSurahTitle(native, namePaint, ready.head.surahId, g.lineHeight * RAIL_INK, margin, g.headY)
            } else if (ready.head.surah.isNotEmpty()) {
                railPaint.textAlign = Paint.Align.LEFT
                native.drawText(context.getString(R.string.mushaf_surah_title, ready.head.surah), margin, g.headY + railBaseline, railPaint)
            }
            railPaint.textAlign = Paint.Align.CENTER
            native.drawText(MushafHeader.digits(ready.data.page), this.size.width / 2f, g.footY + railBaseline, railPaint)
        }
        val ruleY = g.headY + g.lineHeight * HEAD_RULE_OFFSET
        drawLine(ink.copy(alpha = 0.3f), Offset(margin, ruleY), Offset(this.size.width - margin, ruleY), strokeWidth = 1.dp.toPx())

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
                        framePaint.color = frameColor
                        namePaint.color = inkArgb
                        drawSurahHeader(native, framePaint, namePaint, placed.line.surahId, placed.centerY, g.lineHeight, margin, this.size.width)
                        if (placed.line.surahId !in 1..SURAHS) {
                            headerPaint.textSize = g.lineHeight * 0.34f
                            val nameBaseline = -(headerPaint.fontMetrics.ascent + headerPaint.fontMetrics.descent) / 2f
                            native.drawText(
                                context.getString(R.string.mushaf_surah_title, placed.line.surahNameArabic.orEmpty()),
                                this.size.width / 2f, placed.centerY + nameBaseline, headerPaint
                            )
                            headerPaint.textSize = g.lineHeight * 0.45f
                        }
                    }
                    MushafLineType.BASMALLAH -> {
                        framePaint.color = inkArgb
                        drawGlyph(native, framePaint, QuranGlyphs.BASMALLAH, g.lineHeight * BASMALLAH_INK, this.size.width / 2f, placed.centerY, Paint.Align.CENTER)
                    }
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

    }
}

private const val SURAHS = 114
private const val JUZ = 30

/** How tall the ink of the running head's calligraphy is, and of the basmallah, in lines. */
private const val RAIL_INK = 0.58f
private const val BASMALLAH_INK = 0.65f

// The pointed panel in the banner, in 1/1024 em of the banner's font: 762 high and about 3400 wide where it is level.
private const val PANEL_HEIGHT = 762f
private const val PANEL_WIDTH = 3400f
private const val TITLE_GAP = 0.18f

/** Gold on the page, as the printed banner has it; paler on a dark page, where [ink] is light. */
private fun surahFrameColor(ink: Color): Color =
    if (ink.luminance() > 0.5f) Color(0xFFBFA05A) else Color(0xFF8A6D2F)

/** The width of "سورة" and a surah's name side by side, in em. */
private fun surahTitleEm(paint: Paint, surah: Int): Float {
    paint.textSize = QuranGlyphs.UNITS
    return (paint.inkWidth(QuranGlyphs.surahName(surah).text) + paint.inkWidth(QuranGlyphs.SURAH_WORD.text)) / QuranGlyphs.UNITS + TITLE_GAP
}

/** The word "سورة" and [surah]'s name, the name first as the pair reads right to left, with its ink [inkHeight] tall. */
private fun drawSurahTitle(canvas: android.graphics.Canvas, paint: Paint, surah: Int, inkHeight: Float, left: Float, centerY: Float) {
    val name = QuranGlyphs.surahName(surah)
    drawGlyph(canvas, paint, name, inkHeight, left, centerY)
    val nameWidth = paint.inkWidth(name.text)
    drawGlyph(canvas, paint, QuranGlyphs.SURAH_WORD, inkHeight, left + nameWidth + paint.textSize * TITLE_GAP, centerY)
}

/**
 * The banner the printed mushaf puts round a surah's name, as QUL draws it: the banner glyph across the page, and in
 * its panel the word "سورة" and the surah's name in calligraphy. The banner is one line tall and spans the text.
 */
private fun drawSurahHeader(
    canvas: android.graphics.Canvas, framePaint: Paint, namePaint: Paint,
    surah: Int, centerY: Float, lineHeight: Float, margin: Float, width: Float
) {
    val banner = QuranGlyphs.BANNER
    val frameSize = lineHeight * QuranGlyphs.UNITS / banner.inkUnits
    framePaint.textSize = frameSize
    framePaint.textScaleX = 1f
    val natural = framePaint.measureText(banner.text).takeIf { it > 0f } ?: return
    framePaint.textScaleX = (width - 2f * margin) / natural
    framePaint.textAlign = Paint.Align.LEFT
    canvas.drawText(banner.text, margin, centerY + frameSize * banner.centerUnits / QuranGlyphs.UNITS, framePaint)
    if (surah !in 1..SURAHS) return

    // The name is as tall as the panel (its ink is about three quarters of that), and shrinks only if the pair is wider than the panel.
    val pairEm = surahTitleEm(namePaint, surah)
    val size = minOf(frameSize * PANEL_HEIGHT / QuranGlyphs.UNITS, frameSize * PANEL_WIDTH * 0.9f / QuranGlyphs.UNITS / pairEm)
    drawSurahTitle(canvas, namePaint, surah, size, width / 2f - pairEm * size / 2f, centerY)
}
