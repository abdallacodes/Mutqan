package com.example.mutqan.domain

import com.example.mutqan.quran.QuranSurah
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SurahChoicesTest {
    private val verses = intArrayOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
        112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
        54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
        14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
        29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
        11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6
    )
    private val madani = setOf(2, 3, 4, 5, 8, 9, 22, 24, 33, 47, 48, 49, 55, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 76, 98, 99, 110)

    private val all = verses.mapIndexed { i, count ->
        val id = i + 1
        QuranSurah(id, "s$id", "s$id", "s$id", if (id in madani) "madinah" else "makkah", id, count, true, id, id * 30 / 115 + 1)
    }

    private fun surah(id: Int) = all.first { it.id == id }

    @Test
    fun theFixtureIsTheWholeQuran() {
        assertEquals(114, all.size)
        assertEquals(6236, verses.sum())
    }

    @Test
    fun aLongSurahIsOnlyOfferedBesideSurahsOfSimilarLength() {
        repeat(300) { seed ->
            val others = SurahChoices.distractors(surah(2), all, emptyList(), 4, Random(seed))
            assertEquals(4, others.size)
            assertTrue("$others", others.all { surah(it).versesCount * 5 >= 286 })
        }
    }

    @Test
    fun aShortSurahIsOnlyOfferedBesideShortOnes() {
        repeat(300) { seed ->
            val others = SurahChoices.distractors(surah(108), all, emptyList(), 4, Random(seed))
            assertEquals(4, others.size)
            assertTrue("$others", others.all { surah(it).versesCount <= 15 })
        }
    }

    @Test
    fun noSurahIsOfferedTwiceOrAgainstItself() {
        repeat(100) { seed ->
            val answer = 1 + seed % 114
            val others = SurahChoices.distractors(surah(answer), all, listOf(answer, 7, 7), 4, Random(seed))
            assertEquals(4, others.size)
            assertEquals(others.size, others.toSet().size)
            assertFalse(answer in others)
        }
    }

    @Test
    fun surahsWorkedLikeTheVerseComeFirstButNoMoreThanTwo() {
        val others = SurahChoices.distractors(surah(2), all, listOf(111, 112, 113), 4, Random(1))
        assertEquals(listOf(111, 112), others.take(2))
        assertEquals(4, others.size)
        // The third look-alike is not special: it is in only if it is like the answer.
        assertFalse(113 in others)
    }

    @Test
    fun theChoicesVaryFromAskToAsk() {
        val sets = (0 until 40).map { SurahChoices.distractors(surah(36), all, emptyList(), 4, Random(it)).toSet() }.toSet()
        assertTrue(sets.size > 5)
    }

    @Test
    fun aSurahNextToTheAnswerIsPreferredToOneFarAway() {
        // Equal in length and place: the neighbour (Al-Hijr is 99 verses, Al-Hijr's neighbour An-Nahl 128) beats a distant one.
        val near = SurahChoices.closeness(surah(15), surah(16))
        val far = SurahChoices.closeness(surah(15), surah(21))
        assertTrue(near > far)
    }

    @Test
    fun fewerSurahsThanWantedGivesWhatThereIs() {
        val three = all.take(3)
        assertEquals(2, SurahChoices.distractors(three[0], three, emptyList(), 4, Random(0)).size)
    }
}
