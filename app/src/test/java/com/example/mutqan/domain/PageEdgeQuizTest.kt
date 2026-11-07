package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PageEdgeQuizTest {
    @Test
    fun interleaveTakesOneFromEachListInTurn() {
        assertEquals(listOf("a", 1, "x", "b", "y", "c").map { it.toString() }, QuizText.interleave(listOf(listOf("a", "b", "c"), listOf("1"), listOf("x", "y"))))
        assertEquals(emptyList<Int>(), QuizText.interleave(emptyList<List<Int>>()))
        assertEquals(listOf(1, 2), QuizText.interleave(listOf(emptyList(), listOf(1, 2))))
    }

    @Test
    fun forwardGoesOnAndComesRoundToTheFirstPage() {
        assertEquals(6, PageOrder.FORWARD.next(5, emptyList(), emptyList()))
        assertEquals(1, PageOrder.FORWARD.next(604, emptyList(), emptyList()))
    }

    @Test
    fun stayKeepsThePage() {
        assertEquals(77, PageOrder.STAY.next(77, listOf(1, 2, 3), emptyList()))
    }

    @Test
    fun randomDrawsFromThePoolAndAvoidsWhatItJustUsed() {
        val pool = listOf(10, 11, 12, 13)
        repeat(100) { seed ->
            val next = PageOrder.RANDOM.next(10, pool, recent = listOf(11), random = Random(seed))
            assertTrue(next in listOf(12, 13))
        }
        // With a single page in the pool it is asked again rather than nothing.
        assertEquals(10, PageOrder.RANDOM.next(10, listOf(10), emptyList()))
        // With no pool the page stays.
        assertEquals(9, PageOrder.RANDOM.next(9, emptyList(), emptyList()))
        assertNotEquals(PageOrder.FORWARD, PageOrder.RANDOM)
    }
}
