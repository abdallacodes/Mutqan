package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhraseIndexTest {
    private fun phrase(id: Int, text: String, vararg verses: Triple<Int, Int, Int>) =
        ReadyPhrase(id, text, verses.map { PhraseVerse(it.first, it.second, it.third) })

    private val phrases = listOf(
        // Two words, in three verses.
        phrase(1, "a b", Triple(10, 1, 2), Triple(20, 3, 4), Triple(30, 1, 2)),
        // Four words, in two verses.
        phrase(2, "a b c d", Triple(10, 1, 4), Triple(40, 2, 5)),
        // Two words in many verses.
        phrase(3, "x y", Triple(10, 6, 7), Triple(50, 1, 2), Triple(60, 1, 2), Triple(70, 1, 2), Triple(80, 1, 2)),
        // Said twice in one verse only.
        phrase(4, "q r", Triple(90, 1, 2), Triple(90, 5, 6))
    )

    @Test
    fun theLongestPhraseComesFirstThenTheRarest() {
        val found = PhraseIndex(phrases).phrasesOf(10)
        assertEquals(listOf(2, 1, 3), found.map { it.id })
        assertEquals(1..4, found[0].here)
        assertEquals(listOf(40), found[0].others.map { it.ayahId })
        assertEquals(listOf(20, 30), found[1].others.map { it.ayahId })
    }

    @Test
    fun aPhraseOnlyOneVerseHasIsLeftOut() {
        assertTrue(PhraseIndex(phrases).phrasesOf(90).isEmpty())
        assertTrue(PhraseIndex(phrases).phrasesOf(999).isEmpty())
    }

    @Test
    fun aVerseInAPhraseTwiceIsListedOnce() {
        val twice = listOf(phrase(5, "m n", Triple(10, 1, 2), Triple(20, 1, 2), Triple(20, 7, 8)))
        assertEquals(listOf(20), PhraseIndex(twice).phrasesOf(10).single().others.map { it.ayahId })
    }
}
