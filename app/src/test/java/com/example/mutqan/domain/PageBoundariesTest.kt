package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PageBoundariesTest {

    private fun covered(vararg pages: IntRange): BooleanArray =
        BooleanArray(604).also { arr -> pages.forEach { r -> r.forEach { arr[it - 1] = true } } }

    @Test
    fun `the neighbour exercise needs a page on both sides`() {
        assertFalse(PageBoundaries.isAskable(1, ExerciseType.NEIGHBOURS))
        assertFalse(PageBoundaries.isAskable(604, ExerciseType.NEIGHBOURS))
        assertTrue(PageBoundaries.isAskable(2, ExerciseType.NEIGHBOURS))
        assertTrue(PageBoundaries.isAskable(1, ExerciseType.PAGE_EDGES))
        assertTrue(PageBoundaries.isAskable(604, ExerciseType.FIRST_TO_LAST))
    }

    @Test
    fun `the pool is the memorised pages that can be asked`() {
        val pool = PageBoundaries.pool(covered(1..5), null, ExerciseType.NEIGHBOURS)
        assertEquals(listOf(2, 3, 4, 5), pool)
    }

    @Test
    fun `a juz narrows the pool`() {
        val pool = PageBoundaries.pool(covered(1..60), (21..40).toList(), ExerciseType.PAGE_EDGES)
        assertEquals((21..40).toList(), pool)
        assertEquals(emptyList<Int>(), PageBoundaries.pool(covered(1..10), (21..40).toList(), ExerciseType.PAGE_EDGES))
    }

    @Test
    fun `without coverage every page is in the pool`() {
        assertEquals(604, PageBoundaries.pool(null, null, ExerciseType.PAGE_EDGES).size)
        assertEquals(602, PageBoundaries.pool(null, null, ExerciseType.NEIGHBOURS).size)
    }

    @Test
    fun `a pick avoids recent pages while others remain`() {
        val random = Random(7)
        repeat(50) {
            val page = PageBoundaries.pick(listOf(10, 11, 12), recent = listOf(10, 11), random = random)
            assertEquals(12, page)
        }
    }

    @Test
    fun `when everything is recent the pool is used again, and an empty pool gives nothing`() {
        assertEquals(10, PageBoundaries.pick(listOf(10), recent = listOf(10)))
        assertNull(PageBoundaries.pick(emptyList(), recent = emptyList()))
    }

    @Test
    fun `exercise codes are stable`() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12), ExerciseType.entries.map { it.code })
        assertEquals(listOf(4, 5, 12, 7, 8), ExerciseType.QUIZ.map { it.code })
        assertEquals(ExerciseType.NEIGHBOURS, ExerciseType.fromCode(3))
        assertNull(ExerciseType.fromCode(99))
    }

    @Test
    fun `the verse shown is never the answer`() {
        val verses = listOf(10, 11, 12, 13)
        repeat(50) {
            assertTrue(PageBoundaries.promptVerse(verses, ExerciseType.AYAH_PAGE_START) != 10)
            assertTrue(PageBoundaries.promptVerse(verses, ExerciseType.AYAH_PAGE_END) != 13)
        }
        assertEquals(7, PageBoundaries.promptVerse(listOf(7), ExerciseType.AYAH_PAGE_START))
        assertEquals(null, PageBoundaries.promptVerse(emptyList(), ExerciseType.AYAH_PAGE_END))
    }

    @Test
    fun `the new exercises are recorded under their own codes`() {
        assertEquals(ExerciseType.AYAH_PAGE_START, ExerciseType.fromCode(10))
        assertEquals(ExerciseType.AYAH_PAGE_END, ExerciseType.fromCode(11))
        assertTrue(ExerciseType.AYAH_PAGE_END in ExerciseType.PAGE_BOUNDARIES)
    }
}
