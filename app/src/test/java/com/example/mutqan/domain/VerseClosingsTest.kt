package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class VerseClosingsTest {

    private fun verse(text: String) = text.split(" ")

    private val verses = listOf(
        verse("a1 a2 in allah ghafur rahim"),
        verse("b1 b2 in allah azeez hakeem"),
        verse("c1 c2 in allah samee aleem"),
        verse("d1 d2 in allah ghafur haleem"),
        verse("e1 e2 in allah tawwab rahim"),
        verse("f1 f2 f3 f4 f5 f6")
    )

    @Test
    fun `closings that follow the same two words are the rivals, and the real one is not`() {
        val choice = VerseClosings(verses).choiceFor(1, Random(1))
        assertNotNull(choice)
        choice!!
        assertEquals(2, choice.length)
        assertEquals(setOf(2, 3, 4, 5), choice.rivals.map { it.ayahId }.toSet())
        assertFalse(choice.rivals.any { it.ending == listOf("ghafur", "rahim") })
    }

    @Test
    fun `a verse whose closing nobody else shares has no choice`() {
        assertNull(VerseClosings(verses).choiceFor(6, Random(1)))
    }

    @Test
    fun `fewer than three different closings is not enough`() {
        assertNull(VerseClosings(verses.take(3)).choiceFor(1, Random(1)))
    }

    @Test
    fun `diacritics do not make two closings different`() {
        val marked = listOf(
            verse("a1 a2 إِنَّ ٱللَّهَ غَفُورٌ رَّحِيمٌ"),
            verse("b1 b2 إِنَّ ٱللَّهَ عَزِيزٌ حَكِيمٌ"),
            verse("c1 c2 إِنَّ ٱللَّهَ سَمِيعٌ عَلِيمٌ"),
            verse("d1 d2 إن الله غفور رحيم"),
            verse("e1 e2 إِنَّ ٱللَّهَ تَوَّابٌ رَّحِيمٌ")
        )
        val choice = VerseClosings(marked).choiceFor(1, Random(2))!!
        // The fourth verse closes exactly as the first once marks are ignored, so it cannot be a rival.
        assertFalse(4 in choice.rivals.map { it.ayahId })
        assertTrue(choice.rivals.size >= 3)
    }

    @Test
    fun `an unknown verse has no choice`() {
        assertNull(VerseClosings(verses).choiceFor(99, Random(1)))
    }
}
