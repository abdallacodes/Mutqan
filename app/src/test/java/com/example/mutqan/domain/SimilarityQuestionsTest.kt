package com.example.mutqan.domain

import com.example.mutqan.quran.QuranWord
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarityQuestionsTest {

    private fun w(text: String) = text.split(" ").mapIndexed { i, t -> QuranWord(i + 1, 1, 1, i + 1, t, t, false, 1) }
    private fun strings(text: String) = text.split(" ")

    @Test
    fun `common prefix counts the shared opening words`() {
        assertEquals(3, SimilarityQuestions.commonPrefix(strings("a b c d"), strings("a b c x y")))
        assertEquals(0, SimilarityQuestions.commonPrefix(strings("a b"), strings("b a")))
    }

    @Test
    fun `finish offers the ending of each verse that shares the opening`() {
        val verse = w("one two three red fish today")
        val others = listOf(w("one two three blue fish today"), w("one two nine green"))
        val set = SimilarityQuestions.finish(verse, others, Random(1))!!
        assertTrue(set.lead.plain.contains("three"))
        // Only the lookalike that shares the whole opening ("one two three") is a rival.
        assertEquals(2, set.options.size)
        assertTrue(set.options[set.correct].plain.startsWith("red"))
        assertTrue(set.options.any { it.plain.startsWith("blue") })
    }

    @Test
    fun `finish reaches further when the first words of the endings agree`() {
        val verse = w("a b same same X end")
        val other = w("a b same same Y end")
        val set = SimilarityQuestions.finish(verse, listOf(other), Random(2))!!
        assertEquals(2, set.options.size)
        assertTrue(set.options[set.correct].plain.contains("X"))
    }

    @Test
    fun `finish works on a stretch shared in the middle of the verse`() {
        val verse = w("alpha beta gamma delta red sky")
        val other = w("zeta gamma delta blue sea")
        val set = SimilarityQuestions.finish(verse, listOf(other), Random(6))!!
        assertTrue(set.lead.plain.contains("delta"))
        assertEquals(2, set.options.size)
        assertTrue(set.options[set.correct].plain.startsWith("red"))
        assertTrue(set.options.any { it.plain.startsWith("blue") })
    }

    @Test
    fun `finish needs a lookalike that shares a stretch`() {
        assertNull(SimilarityQuestions.finish(w("a b c"), listOf(w("x y z")), Random(3)))
        assertNull(SimilarityQuestions.finish(w("a b c"), emptyList(), Random(3)))
    }

    @Test
    fun `which offers the references of the group`() {
        val set = SimilarityQuestions.which(
            w("a b c"), "S1 · 1", listOf("S2 · 5" to w("a b d"), "S3 · 7" to w("a x c")), Random(4)
        )!!
        assertEquals(3, set.options.size)
        assertEquals("S1 · 1", set.options[set.correct].plain)
    }

    @Test
    fun `which is skipped when a lookalike is word for word the same`() {
        assertNull(SimilarityQuestions.which(w("a b c"), "S1 · 1", listOf("S2 · 5" to w("a b c")), Random(5)))
        assertNotNull(SimilarityQuestions.which(w("a b c"), "S1 · 1", listOf("S2 · 5" to w("a b d")), Random(5)))
    }
}
