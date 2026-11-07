package com.example.mutqan.domain

import com.example.mutqan.quran.QuranRub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuarterMapTest {

    private fun rub(number: Int, startPage: Int, endPage: Int) = QuranRub(
        number = number, juz = (number - 1) / 8 + 1, hizb = (number - 1) / 4 + 1, quarterInJuz = (number - 1) % 8 + 1,
        startAyahId = number * 10, endAyahId = number * 10 + 9, startPage = startPage, endPage = endPage
    )

    @Test
    fun `health is the mean of the revised pages and counts them`() {
        val scores = mapOf(10 to 0.9f, 11 to 0.5f)
        val h = QuarterMap.health(rub(1, 10, 12)) { scores[it] }
        assertEquals(0.7f, h.average!!, 0.0001f)
        assertEquals(2, h.tracked)
        assertEquals(3, h.total)
    }

    @Test
    fun `a quarter with no revised page has no strength`() {
        val h = QuarterMap.health(rub(1, 10, 12)) { null }
        assertNull(h.average)
        assertEquals(0, h.tracked)
    }

    @Test
    fun `the weakest quarter is the lowest of those revised, earliest on a tie`() {
        val quarters = listOf(
            QuarterHealth(null, 0, 2),
            QuarterHealth(0.8f, 2, 2),
            QuarterHealth(0.3f, 1, 2),
            QuarterHealth(0.3f, 2, 2)
        )
        assertEquals(2, QuarterMap.weakest(quarters))
    }

    @Test
    fun `nothing is weakest when nothing was revised`() {
        assertNull(QuarterMap.weakest(listOf(QuarterHealth(null, 0, 2), QuarterHealth(null, 0, 3))))
        assertNull(QuarterMap.weakest(emptyList()))
    }

    @Test
    fun `the subject shown is the one the reader is in, else the first of the quarter`() {
        val quarter = rub(5, 20, 22) // ayahs 50..59
        val subjects = listOf(52 to "A", 56 to "B", 70 to "elsewhere")
        assertEquals("A", QuarterMap.subjectAt(quarter, subjects, 54))
        assertEquals("B", QuarterMap.subjectAt(quarter, subjects, 58))
        assertEquals("A", QuarterMap.subjectAt(quarter, subjects, 50))
        assertNull(QuarterMap.subjectAt(quarter, listOf(70 to "elsewhere"), 54))
    }

    @Test
    fun `the following quarter crosses into the next juz and stops at the end`() {
        val juz1 = (1..8).map { rub(it, it, it) }
        val juz2 = (9..16).map { rub(it, it, it) }
        assertEquals(4, QuarterMap.following(juz1[2], juz1, juz2)?.number)
        assertEquals(9, QuarterMap.following(juz1[7], juz1, juz2)?.number)
        assertNull(QuarterMap.following(rub(240, 604, 604), listOf(rub(240, 604, 604)), emptyList()))
    }
}
