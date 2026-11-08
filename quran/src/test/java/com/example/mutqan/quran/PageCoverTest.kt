package com.example.mutqan.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCoverTest {
    // An ayah (id 7) of four read words, ids 100..103, closed by the number marker, id 104.
    private val first = 100
    private val last = 103
    private val marker = 104

    private fun cover(partial: PartialReveal? = null, whole: Set<Int> = emptySet()) =
        PageCover(whole, if (partial == null) emptyMap() else mapOf(7 to partial))

    @Test
    fun `an ayah that is not revealed hides every word`() {
        val c = cover()
        for (id in first..marker) assertTrue(c.hides(7, id))
    }

    @Test
    fun `a whole ayah hides nothing`() {
        val c = cover(whole = setOf(7))
        for (id in first..marker) assertFalse(c.hides(7, id))
    }

    @Test
    fun `the first step shows only the first word`() {
        val c = cover(PartialReveal(first, last, withLast = false))
        assertFalse(c.hides(7, first))
        for (id in (first + 1)..marker) assertTrue(c.hides(7, id))
    }

    @Test
    fun `the second step shows the first word, the last word and the closing number`() {
        val c = cover(PartialReveal(first, last, withLast = true))
        assertFalse(c.hides(7, first))
        assertTrue(c.hides(7, first + 1))
        assertTrue(c.hides(7, last - 1))
        assertFalse(c.hides(7, last))
        assertFalse(c.hides(7, marker))
    }

    @Test
    fun `a partial ayah does not uncover another one`() {
        val c = cover(PartialReveal(first, last, withLast = true))
        assertTrue(c.hides(8, 200))
    }

    @Test
    fun `words with no ayah are never hidden`() {
        assertEquals(false, cover().hides(0, 5))
    }
}
