package com.example.mutqan.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MushafPageGeometryTest {

    // Every glyph is 10 units wide at the measure size (100), a space is 5.
    private val advance: (String) -> Float = { 10f }
    private val space = 5f

    private fun ayah(number: Int, words: Int, centered: Boolean = false) =
        MushafLine(number, MushafLineType.AYAH, centered, List(words) { "w$it" }, null)

    private fun geometry(vararg lines: MushafLine, width: Float = 900f, height: Float = 1500f) =
        MushafPageGeometry.compute(MushafPageData(1, lines.toList()), width, height, advance, space)

    @Test
    fun justifiedLineSpansFromRightMarginToLeftMargin() {
        val g = geometry(ayah(1, 10), ayah(2, 6))
        val margin = 900f * 0.045f
        val scale = g.textSize / MushafPageGeometry.MEASURE_SIZE
        val wordWidth = 10f * scale
        for (placed in g.lines) {
            val first = placed.words.first()
            val last = placed.words.last()
            assertEquals(900f - margin, first.x + wordWidth, 0.01f)
            assertEquals(margin, last.x, 0.01f)
        }
    }

    @Test
    fun widestLineFixesTextSizeAtNaturalSpacing() {
        // widest natural line = 10 words * 10 + 9 spaces * 5 = 145 at size 100
        val g = geometry(ayah(1, 10), ayah(2, 6), height = 100_000f)
        val available = 900f * (1 - 2 * 0.045f)
        assertEquals(100f * available / 145f, g.textSize, 0.01f)
    }

    @Test
    fun rowHeightCapsTextSizeWhenPageIsShort() {
        val g = geometry(ayah(1, 3), height = 300f)
        assertEquals(300f / 15 * 0.95f, g.textSize, 0.01f)
    }

    @Test
    fun centredLineKeepsNaturalWidthAndIsCentred() {
        val g = geometry(ayah(1, 10), ayah(2, 4, centered = true))
        val scale = g.textSize / MushafPageGeometry.MEASURE_SIZE
        val words = g.lines[1].words
        val natural = 4 * 10f * scale + 3 * space * scale
        val left = words.last().x
        val right = words.first().x + 10f * scale
        assertEquals(natural, right - left, 0.01f)
        assertEquals(450f, (left + right) / 2f, 0.01f)
    }

    @Test
    fun firstWordIsRightmost() {
        val g = geometry(ayah(1, 5))
        val xs = g.lines[0].words.map { it.x }
        assertTrue(xs.zipWithNext().all { (a, b) -> a > b })
        assertEquals("w0", g.lines[0].words.first().glyph)
    }

    @Test
    fun shortPageIsCentredVerticallyOnTheFifteenRowGrid() {
        val g = geometry(ayah(1, 5), ayah(2, 5), height = 1500f)
        val rowHeight = 100f
        val top = (1500f - 2 * rowHeight) / 2f
        assertEquals(top + rowHeight / 2f, g.lines[0].centerY, 0.01f)
        assertEquals(top + rowHeight * 1.5f, g.lines[1].centerY, 0.01f)
    }

    @Test
    fun headerLinesGetNoWords() {
        val header = MushafLine(1, MushafLineType.SURAH_NAME, true, emptyList(), "x")
        val g = geometry(header, ayah(2, 4))
        assertTrue(g.lines[0].words.isEmpty())
    }
}
