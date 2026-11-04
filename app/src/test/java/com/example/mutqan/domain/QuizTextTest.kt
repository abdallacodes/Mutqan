package com.example.mutqan.domain

import com.example.mutqan.quran.MushafSpan
import com.example.mutqan.quran.QuranWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizTextTest {

    private fun w(text: String) = text.split(" ").mapIndexed { i, t -> QuranWord(i + 1, 1, 1, i + 1, t, t, false, 1) }
    private fun span(text: String) = MushafSpan.of(w(text))

    @Test
    fun `opening and tail add an ellipsis only when words are left out`() {
        val words = w("a b c d")
        assertEquals("a b …", QuizText.opening(words, 2).plain)
        assertEquals("a b c d", QuizText.opening(words, 4).plain)
        assertEquals("… c d", QuizText.tail(words, 2).plain)
        assertEquals("a b c d", QuizText.tail(words, 9).plain)
    }

    @Test
    fun `options start with the answer and drop repeats and blanks`() {
        val options = QuizText.options(span("بسم"), listOf("بِسْمِ", "", "الله", "الله", "الرحمن", "الرحيم").map { MushafSpan.label(it) }, max = 4)
        // "بِسْمِ" equals the answer once diacritics are ignored, so it is dropped.
        assertEquals(listOf("بسم", "الله", "الرحمن", "الرحيم"), options.map { it.plain })
    }

    @Test
    fun `options can be fewer than asked when there are not enough different ones`() {
        assertEquals(listOf("x"), QuizText.options(span("x"), listOf(span("x")), max = 4).map { it.plain })
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
