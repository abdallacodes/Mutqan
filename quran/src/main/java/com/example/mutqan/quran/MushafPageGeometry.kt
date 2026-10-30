package com.example.mutqan.quran

/** A word's glyph, the x coordinate of its left edge, its width, and its word and ayah ids (0 if unknown). */
internal data class PlacedWord(val glyph: String, val x: Float, val width: Float, val wordId: Int = 0, val ayahId: Int = 0)

/** A horizontal stretch of one line covered by a single ayah: [left]..[right] at row [centerY]. */
internal data class AyahRun(val ayahId: Int, val left: Float, val right: Float, val centerY: Float)

/** [centerY] is the vertical centre of the line's row; [words] is empty for header lines. */
internal data class PlacedLine(val line: MushafLine, val centerY: Float, val words: List<PlacedWord>)

internal data class PageGeometry(val textSize: Float, val lineHeight: Float, val lines: List<PlacedLine>)

/**
 * Lays out a QPC V2 Mushaf page: 15 equal rows, text size chosen so the widest line exactly
 * fills the page width, justified lines stretched by widening the gaps between words, and
 * centred lines (surah starts, the two opening pages) kept at their natural width.
 *
 * Pure maths so it can be unit-tested; the caller supplies glyph advances measured with the
 * page's own font at [MEASURE_SIZE].
 */
internal object MushafPageGeometry {

    const val LINES_PER_PAGE = 15
    const val MEASURE_SIZE = 100f

    /** Width / height of a laid-out page, used to size the canvas. */
    const val PAGE_ASPECT = 900f / 1400f

    private const val MARGIN_FRACTION = 0.045f
    private const val ROW_FILL = 0.95f

    fun compute(
        page: MushafPageData,
        width: Float,
        height: Float,
        advanceAtMeasureSize: (String) -> Float,
        spaceAtMeasureSize: Float
    ): PageGeometry {
        val margin = width * MARGIN_FRACTION
        val available = width - 2 * margin
        val lineHeight = height / LINES_PER_PAGE

        val ayahLines = page.lines.filter { it.type == MushafLineType.AYAH }
        val naturalWidths = ayahLines.associateWith { line ->
            line.words.sumOf { advanceAtMeasureSize(it).toDouble() }.toFloat() +
                spaceAtMeasureSize * (line.words.size - 1).coerceAtLeast(0)
        }
        val widest = naturalWidths.values.maxOrNull() ?: 0f
        val fitToWidth = if (widest > 0f) MEASURE_SIZE * available / widest else Float.MAX_VALUE
        val textSize = minOf(fitToWidth, lineHeight * ROW_FILL)
        val scale = textSize / MEASURE_SIZE

        // Short pages (1 and 2) keep the same row height and sit in the vertical middle.
        val top = (height - lineHeight * page.lines.size).coerceAtLeast(0f) / 2f

        val placed = page.lines.map { line ->
            val centerY = top + (line.number - 1) * lineHeight + lineHeight / 2f
            PlacedLine(line, centerY, if (line.type == MushafLineType.AYAH) placeWords(line, width, margin, scale, advanceAtMeasureSize, spaceAtMeasureSize) else emptyList())
        }
        return PageGeometry(textSize, lineHeight, placed)
    }

    /**
     * The word under ([x], [y]), or null. A tap in the gap between two words, or just past the
     * end of a line, still counts for the nearest word of that row (within a third of a row).
     */
    fun hitTest(geometry: PageGeometry, x: Float, y: Float): PlacedWord? {
        val row = geometry.lines.firstOrNull { it.words.isNotEmpty() && kotlin.math.abs(y - it.centerY) <= geometry.lineHeight / 2f }
            ?: return null
        val slack = geometry.lineHeight / 3f
        return row.words
            .map { it to when { x < it.x -> it.x - x; x > it.x + it.width -> x - (it.x + it.width); else -> 0f } }
            .minByOrNull { it.second }
            ?.takeIf { it.second <= slack }
            ?.first
    }

    /** Stretches of each line covered by one of [ayahIds], gaps between neighbouring words included. */
    fun ayahRuns(geometry: PageGeometry, ayahIds: Set<Int>): List<AyahRun> {
        if (ayahIds.isEmpty()) return emptyList()
        val runs = ArrayList<AyahRun>()
        for (placed in geometry.lines) {
            val words = placed.words
            var i = 0
            while (i < words.size) {
                val id = words[i].ayahId
                if (id !in ayahIds) { i++; continue }
                var j = i
                while (j + 1 < words.size && words[j + 1].ayahId == id) j++
                // words run right to left, so words[i] is the right-most of the run
                runs.add(AyahRun(id, words[j].x, words[i].x + words[i].width, placed.centerY))
                i = j + 1
            }
        }
        return runs
    }

    private fun placeWords(
        line: MushafLine,
        width: Float,
        margin: Float,
        scale: Float,
        advance: (String) -> Float,
        spaceAtMeasureSize: Float
    ): List<PlacedWord> {
        val widths = line.words.map { advance(it) * scale }
        val gap: Float
        var right: Float
        if (line.isCentered) {
            gap = spaceAtMeasureSize * scale
            right = (width + widths.sum() + gap * (widths.size - 1).coerceAtLeast(0)) / 2f
        } else {
            gap = ((width - 2 * margin - widths.sum()) / (widths.size - 1).coerceAtLeast(1)).coerceAtLeast(0f)
            right = width - margin
        }
        return line.words.mapIndexed { i, glyph ->
            right -= widths[i]
            PlacedWord(glyph, right, widths[i], line.wordIds.getOrElse(i) { 0 }, line.ayahIds.getOrElse(i) { 0 })
                .also { right -= gap }
        }
    }
}
