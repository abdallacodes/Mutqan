package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerseListsTest {

    /** Al-Fatiha is verses 1..7, Al-Baqarah 8..293. */
    private val surahOf: (Int) -> Int = { if (it <= 7) 1 else 2 }

    private fun spans(passages: List<ListPassage>) = passages.map { it.start..it.end }

    @Test
    fun aVerseInsideAPassageChangesNothing() {
        val list = listOf(ListPassage(10, 14, "note", 5))
        val (out, how) = VerseLists.add(list, 12, 12, 9, surahOf)
        assertEquals(ListAdd.ALREADY, how)
        assertEquals(list, out)
    }

    @Test
    fun aVerseRunningOnFromAPassageInTheSameSurahMakesItLonger() {
        val (out, how) = VerseLists.add(listOf(ListPassage(10, 14, "note", 5)), 15, 16, 9, surahOf)
        assertEquals(ListAdd.GREW, how)
        assertEquals(listOf(10..16), spans(out))
        assertEquals("note", out[0].note)
        assertEquals(5L, out[0].createdAt)
    }

    @Test
    fun theFirstVerseOfTheNextSurahIsAPassageOfItsOwn() {
        val (out, how) = VerseLists.add(listOf(ListPassage(5, 7)), 8, 8, 9, surahOf)
        assertEquals(ListAdd.NEW, how)
        assertEquals(listOf(5..7, 8..8), spans(out))
    }

    @Test
    fun versesBetweenTwoPassagesJoinThemWithBothNotes() {
        val list = listOf(ListPassage(10, 11, "a"), ListPassage(14, 15, "b"))
        val (out, how) = VerseLists.add(list, 12, 13, 9, surahOf)
        assertEquals(ListAdd.GREW, how)
        assertEquals(listOf(10..15), spans(out))
        assertEquals("a\nb", out[0].note)
    }

    @Test
    fun passagesStayInReadingOrder() {
        val (out, _) = VerseLists.add(listOf(ListPassage(100, 101)), 20, 20, 9, surahOf)
        assertEquals(listOf(20..20, 100..101), spans(out))
    }

    @Test
    fun addingManyVersesMakesOnePassagePerRunAndCountsTheNewOnes() {
        val (out, added) = VerseLists.addAll(listOf(ListPassage(30, 30)), listOf(7, 8, 9, 30, 31, 50), 9, surahOf)
        assertEquals(listOf(7..7, 8..9, 30..31, 50..50), spans(out))
        assertEquals(5, added)
    }

    @Test
    fun takingVersesOutShortensOrSplitsAPassage() {
        val list = listOf(ListPassage(10, 20, "n"), ListPassage(30, 30))
        assertEquals(listOf(10..14, 18..20, 30..30), spans(VerseLists.remove(list, 15, 17)))
        assertEquals(listOf(11..20, 30..30), spans(VerseLists.remove(list, 10, 10)))
        assertEquals(listOf(10..20), spans(VerseLists.remove(list, 30, 30)))
        assertTrue(VerseLists.remove(list, 15, 17).all { it.start != 18 || it.note == "n" })
    }

    @Test
    fun containsAllAndAny() {
        val list = listOf(ListPassage(5, 7), ListPassage(8, 9))
        assertTrue(VerseLists.containsAll(list, 6, 9))
        assertFalse(VerseLists.containsAll(list, 6, 10))
        assertTrue(VerseLists.containsAny(list, 9, 12))
        assertFalse(VerseLists.containsAny(list, 10, 12))
        assertEquals(5, VerseLists.verseCount(list))
    }

    @Test
    fun tidyJoinsOverlapsAndKeepsBothNotes() {
        val messy = listOf(ListPassage(20, 25, "b", 7), ListPassage(10, 21, "a", 3), ListPassage(26, 27, "", 9))
        assertEquals(listOf(10..25, 26..27), spans(VerseLists.tidy(messy)))
        val joined = VerseLists.tidy(messy, surahOf)
        assertEquals(listOf(10..27), spans(joined))
        assertEquals("a\nb", joined[0].note)
        assertEquals(3L, joined[0].createdAt)
    }

    @Test
    fun namesDifferingOnlyInDiacriticsOrCaseAreTheSame() {
        assertEquals(VerseLists.nameKey("آيات الصَّبر"), VerseLists.nameKey("  ايات   الصبر "))
        assertEquals(VerseLists.nameKey("Duas"), VerseLists.nameKey("duas"))
        assertEquals("a b", VerseLists.cleanName("  a \n b "))
    }
}
