package com.example.mutqan.quran

import android.graphics.Paint
import android.graphics.Rect
import androidx.annotation.FontRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.core.content.res.ResourcesCompat

/**
 * One glyph of QUL's calligraphy and icon fonts, and where its ink lies: [top] units above the baseline and [bottom]
 * below it (negative), in 1/1024 em. The glyphs have code points of their own, so none needs a ligature to be found.
 */
internal class Glyph(@FontRes val font: Int, val text: String, val top: Float, val bottom: Float) {
    val inkUnits: Float get() = top - bottom
    val centerUnits: Float get() = (top + bottom) / 2f
}

internal object QuranGlyphs {
    const val UNITS = 1024f

    // quran-common.ttf
    val BANNER = Glyph(R.font.quran_common, "", 828f, -188f)
    val BASMALLAH = Glyph(R.font.quran_common, "﷽", 756f, -117f)
    val QURAN = Glyph(R.font.quran_common, "", 996f, -363f)

    /** "الجزء الأول" and so on. */
    fun juzNumber(juz: Int) = Glyph(R.font.quran_common, (0xE000 + juz).toChar().toString(), 820f, -200f)

    /** A juz by its opening words, as it is traditionally called: الم, سيقول, … */
    fun juzName(juz: Int) = Glyph(R.font.quran_common, (0xE900 + juz - 1).toChar().toString(), 1360f, -650f)

    fun revelation(makkah: Boolean) =
        if (makkah) Glyph(R.font.quran_common, "", 1542f, -969f) else Glyph(R.font.quran_common, "", 1572f, -964f)

    // surah-name-v4.ttf: each name is one glyph whose ink is exactly 1em tall
    val SURAH_WORD = Glyph(R.font.surah_name_v4, "", 882f, -142f)
    fun surahName(surah: Int) = Glyph(R.font.surah_name_v4, (0xE000 + surah).toChar().toString(), 882f, -142f)
}

/** How far right of the origin the text reaches: its advance, or further where a glyph's ink runs past it. */
internal fun Paint.inkWidth(text: String): Float {
    val bounds = Rect()
    getTextBounds(text, 0, text.length, bounds)
    return maxOf(measureText(text), bounds.right.toFloat())
}

/** Draws [glyph] with its ink [inkHeight] tall and its ink centred on [centerY]; [paint] carries the glyph's font. */
internal fun drawGlyph(
    canvas: android.graphics.Canvas, paint: Paint, glyph: Glyph, inkHeight: Float, x: Float, centerY: Float,
    align: Paint.Align = Paint.Align.LEFT
) {
    val em = inkHeight * QuranGlyphs.UNITS / glyph.inkUnits
    paint.textSize = em
    paint.textScaleX = 1f
    paint.textAlign = align
    canvas.drawText(glyph.text, x, centerY + em * glyph.centerUnits / QuranGlyphs.UNITS, paint)
}

@Composable
private fun GlyphBox(glyph: Glyph, height: Dp, color: Color, modifier: Modifier, description: String?) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val paint = remember(glyph.font) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = ResourcesCompat.getFont(context, glyph.font) }
    }
    val inkPx = with(density) { height.toPx() }
    val em = inkPx * QuranGlyphs.UNITS / glyph.inkUnits
    val widthPx = remember(glyph.text, em, paint) {
        paint.textSize = em
        paint.inkWidth(glyph.text)
    }
    val semantics = if (description != null) Modifier.semantics { contentDescription = description } else Modifier.clearAndSetSemantics { }
    Canvas(modifier.size(with(density) { widthPx.toDp() }, height).then(semantics)) {
        paint.color = color.toArgb()
        paint.textSize = em
        drawIntoCanvas { it.nativeCanvas.drawText(glyph.text, 0f, em * glyph.top / QuranGlyphs.UNITS, paint) }
    }
}

/** A surah's name in calligraphy, as the printed Mushaf's headings have it; the ink is [height] tall. */
@Composable
fun SurahNameGlyph(surah: Int, height: Dp, color: Color, modifier: Modifier = Modifier, description: String? = null) =
    GlyphBox(QuranGlyphs.surahName(surah), height, color, modifier, description)

/** "الجزء الأول" and so on, in calligraphy; [height] is about the em. */
@Composable
fun JuzNumberGlyph(juz: Int, height: Dp, color: Color, modifier: Modifier = Modifier, description: String? = null) =
    GlyphBox(QuranGlyphs.juzNumber(juz), height, color, modifier, description)

/** A juz by its opening words (الم, سيقول, …), in calligraphy. */
@Composable
fun JuzNameGlyph(juz: Int, height: Dp, color: Color, modifier: Modifier = Modifier, description: String? = null) =
    GlyphBox(QuranGlyphs.juzName(juz), height, color, modifier, description)

/** The Makkah or the Madinah mark, for where a surah was revealed. */
@Composable
fun RevelationGlyph(makkah: Boolean, height: Dp, color: Color, modifier: Modifier = Modifier, description: String? = null) =
    GlyphBox(QuranGlyphs.revelation(makkah), height, color, modifier, description)

/** "القرآن الكريم" in calligraphy. */
@Composable
fun QuranTitleGlyph(height: Dp, color: Color, modifier: Modifier = Modifier, description: String? = null) =
    GlyphBox(QuranGlyphs.QURAN, height, color, modifier, description)
