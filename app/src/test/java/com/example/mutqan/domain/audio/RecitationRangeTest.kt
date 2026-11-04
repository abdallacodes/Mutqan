package com.example.mutqan.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class RecitationRangeTest {

    // Ten verses: 1-4 are surah 1 (page 1), 5-7 surah 2 (pages 1-2), 8-10 surah 2 (page 3).
    private val surah = intArrayOf(0, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2)
    private val page = intArrayOf(0, 1, 1, 1, 1, 1, 2, 2, 3, 3, 3)

    private fun from(start: Int, onPage: Int, toSurahEnd: Boolean) =
        RecitationRange.from(start, onPage, toSurahEnd, 10, { surah[it] }, { page[it] })

    @Test
    fun `a page run stops at the end of the page, across a surah boundary`() {
        assertEquals(listOf(1, 2, 3, 4, 5), from(1, 1, false))
    }

    @Test
    fun `starting in the middle of a page plays to its end`() {
        assertEquals(listOf(3, 4, 5), from(3, 1, false))
    }

    @Test
    fun `a page given later than the verse's own extends the run`() {
        assertEquals(listOf(5, 6, 7), from(5, 2, false))
    }

    @Test
    fun `to the end of the surah ignores pages`() {
        assertEquals(listOf(1, 2, 3, 4), from(1, 1, true))
        assertEquals(listOf(6, 7, 8, 9, 10), from(6, 1, true))
    }

    @Test
    fun `a start outside the Quran gives nothing`() {
        assertEquals(emptyList<Int>(), from(0, 1, false))
        assertEquals(emptyList<Int>(), from(11, 1, false))
    }

    private fun run(run: RecitationRun, start: Int, toSurahEnd: Boolean) =
        run.verses(start, toSurahEnd, 10, { surah[it] }, { page[it] })

    @Test
    fun `a page run grows when the reader turns on going to the end of the surah`() {
        val asked = RecitationRun.Stretch(2)
        assertEquals(listOf(6, 7), run(asked, 6, toSurahEnd = false))
        assertEquals(listOf(6, 7, 8, 9, 10), run(asked, 6, toSurahEnd = true))
    }

    @Test
    fun `a run to the end of the surah shrinks to the page when the reader turns it off`() {
        // Playing on from page 2 into page 3, then switched off: it finishes the page it has reached.
        val asked = RecitationRun.Stretch(2)
        assertEquals(listOf(8, 9, 10), run(asked, 8, toSurahEnd = false))
    }

    @Test
    fun `a whole surah ignores the choice`() {
        assertEquals(listOf(7, 8, 9, 10), run(RecitationRun.Surah, 7, toSurahEnd = false))
        assertEquals(listOf(7, 8, 9, 10), run(RecitationRun.Surah, 7, toSurahEnd = true))
    }

    @Test
    fun `a listed set carries on from the verse sounding and ignores the choice`() {
        val review = RecitationRun.Listed(listOf(1, 2, 3, 8, 9))
        assertEquals(listOf(3, 8, 9), run(review, 3, toSurahEnd = true))
        assertEquals(listOf(3, 8, 9), run(review, 3, toSurahEnd = false))
        assertEquals(emptyList<Int>(), run(review, 5, toSurahEnd = false))
    }
}
