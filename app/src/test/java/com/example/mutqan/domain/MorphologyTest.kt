package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MorphologyTest {

    // 1:1 has words 1..3 (the last without a root), 2:5 has word 2 with root 0 again.
    private val morphology = Morphology(
        roots = listOf("كتب", "رحم"),
        lemmas = listOf("كِتَاب", "كَاتِب", "رَّحْمَٰن"),
        loc = intArrayOf(1_001_001, 1_001_002, 1_001_003, 2_005_002),
        rootOfEntry = intArrayOf(0, 1, -1, 0),
        lemmaOfEntry = intArrayOf(0, 2, 1, 1)
    )

    @Test
    fun `an ayah lists its words with roots and lemmas in order`() {
        assertEquals(
            listOf(MorphWord(1, 0, 0), MorphWord(2, 1, 2), MorphWord(3, -1, 1)),
            morphology.wordsOfAyah(1, 1)
        )
        assertEquals(listOf(MorphWord(2, 0, 1)), morphology.wordsOfAyah(2, 5))
        assertTrue(morphology.wordsOfAyah(3, 1).isEmpty())
    }

    @Test
    fun `occurrences of a root come in reading order with their lemma`() {
        assertEquals(
            listOf(WordLocation(1, 1, 1, 0), WordLocation(2, 5, 2, 1)),
            morphology.occurrencesOfRoot(0)
        )
        assertEquals(listOf(WordLocation(1, 1, 3, 1), WordLocation(2, 5, 2, 1)), morphology.occurrencesOfLemma(1))
        assertEquals(RootInfo(words = 2, verses = 2), morphology.infoOfRoot(0))
    }

    @Test
    fun `a root is found by its letters with or without spaces`() {
        assertEquals(listOf(0), morphology.findRoots("كتب"))
        assertEquals(listOf(0), morphology.findRoots(" ك ت ب "))
        assertTrue(morphology.findRoots("سمو").isEmpty())
        assertEquals("ك ت ب", morphology.rootSpaced(0))
    }
}
