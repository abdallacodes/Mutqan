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
        val rows = MushafPageGeometry.LINES_PER_PAGE + MushafPageGeometry.HEAD_ROWS + MushafPageGeometry.FOOT_ROWS
        assertEquals(300f / rows * 0.95f, g.textSize, 0.01f)
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
        val rows = MushafPageGeometry.LINES_PER_PAGE + MushafPageGeometry.HEAD_ROWS + MushafPageGeometry.FOOT_ROWS
        val rowHeight = 1500f / rows
        val top = MushafPageGeometry.HEAD_ROWS * rowHeight + (15 - 2) * rowHeight / 2f
        assertEquals(top + rowHeight / 2f, g.lines[0].centerY, 0.01f)
        assertEquals(top + rowHeight * 1.5f, g.lines[1].centerY, 0.01f)
    }

    @Test
    fun headerLinesGetNoWords() {
        val header = MushafLine(1, MushafLineType.SURAH_NAME, true, emptyList(), "x")
        val g = geometry(header, ayah(2, 4))
        assertTrue(g.lines[0].words.isEmpty())
    }

    // ── Ids, hit-testing and highlight runs ──────────────────────────────────

    private fun ayahWithIds(number: Int, ayahIds: List<Int>) =
        MushafLine(
            number, MushafLineType.AYAH, false, List(ayahIds.size) { "w$it" }, null,
            wordIds = List(ayahIds.size) { number * 100 + it }, ayahIds = ayahIds
        )

    @Test
    fun placedWordsCarryTheirIds() {
        val g = geometry(ayahWithIds(1, listOf(7, 7, 8)))
        val ids = g.lines.single().words.map { it.wordId to it.ayahId }
        assertEquals(listOf(100 to 7, 101 to 7, 102 to 8), ids)
    }

    @Test
    fun hitTestFindsTheWordUnderThePointAndNullOutsideTheRows() {
        val g = geometry(ayahWithIds(1, listOf(7, 8, 9)))
        val row = g.lines.single()
        val second = row.words[1]
        val hit = MushafPageGeometry.hitTest(g, second.x + second.width / 2f, row.centerY)
        assertEquals(8, hit?.ayahId)
        assertEquals(null, MushafPageGeometry.hitTest(g, second.x, row.centerY + g.lineHeight))
        assertEquals(null, MushafPageGeometry.hitTest(g, -500f, row.centerY))
    }

    @Test
    fun ayahRunsMergeNeighbouringWordsOfOneAyahAndSkipOthers() {
        val g = geometry(ayahWithIds(1, listOf(7, 7, 8, 7)))
        val row = g.lines.single()
        val runs = MushafPageGeometry.ayahRuns(g, setOf(7))
        assertEquals(2, runs.size)
        // first run: words 0 and 1 (right-most two)
        assertEquals(row.words[1].x, runs[0].left, 0.01f)
        assertEquals(row.words[0].x + row.words[0].width, runs[0].right, 0.01f)
        assertEquals(row.words[3].x, runs[1].left, 0.01f)
        assertTrue(MushafPageGeometry.ayahRuns(g, emptySet()).isEmpty())
    }
}
