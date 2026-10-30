package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizTextTest {

    @Test
    fun `opening and tail add an ellipsis only when words are left out`() {
        val words = listOf("a", "b", "c", "d")
        assertEquals("a b …", QuizText.opening(words, 2))
        assertEquals("a b c d", QuizText.opening(words, 4))
        assertEquals("… c d", QuizText.tail(words, 2))
        assertEquals("a b c d", QuizText.tail(words, 9))
    }

    @Test
    fun `options start with the answer and drop repeats and blanks`() {
        val options = QuizText.options("بسم", listOf("بِسْمِ", "", "الله", "الله", "الرحمن", "الرحيم"), max = 4)
        // "بِسْمِ" equals the answer once diacritics are ignored, so it is dropped.
        assertEquals(listOf("بسم", "الله", "الرحمن", "الرحيم"), options)
    }

    @Test
    fun `options can be fewer than asked when there are not enough different ones`() {
        assertEquals(listOf("x"), QuizText.options("x", listOf("x"), max = 4))
    }

    @Test
    fun `a blank replaces the hidden words and the window is cut with ellipses`() {
        val words = (1..10).map { "w$it" }
        assertEquals("w1 w2 _____ w5", QuizText.blanked(words.take(5), 2, 2, window = 9))
        assertEquals("… w4 w5 _____ w8 w9 …", QuizText.blanked(words, 5, 2, window = 2))
    }

    @Test
    fun `a shuffled order is never the original order`() {
        val random = Random(3)
        repeat(100) { assertNotEquals((0 until 4).toList(), QuizText.shuffledOrder(4, random)) }
        assertEquals(listOf(0), QuizText.shuffledOrder(1, random))
    }

    @Test
    fun `a weighted pick follows the weights and skips zero`() {
        val random = Random(5)
        repeat(100) { assertEquals(2, QuizText.weightedPick(intArrayOf(0, 0, 7, 0), random)) }
        assertNull(QuizText.weightedPick(intArrayOf(0, 0), random))
        val counts = IntArray(2)
        repeat(2000) { counts[QuizText.weightedPick(intArrayOf(1, 9), random)!!]++ }
        assertTrue(counts[1] > counts[0] * 4)
    }

    @Test
    fun `quiz modes round trip through their route`() {
        QuizMode.entries.forEach { assertEquals(it, QuizMode.fromRoute(it.route)) }
        assertEquals(QuizMode.DAILY, QuizMode.fromRoute("nonsense"))
    }
}
