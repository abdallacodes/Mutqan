package com.example.mutqan.domain

import com.example.mutqan.data.SurahData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerseSearchTest {

    private val index = VerseIndex(
        listOf(
            listOf("إِنَّ", "ٱللَّهَ", "غَفُورٌ", "رَّحِيمٌ"),
            listOf("يَـٰٓأَيُّهَا", "ٱلَّذِينَ", "ءَامَنُوا۟", "ٱتَّقُوا۟", "ٱللَّهَ"),
            listOf("فَبِأَىِّ", "ءَالَآءِ", "رَبِّكُمَا", "تُكَذِّبَانِ", "فَبِأَىِّ", "ءَالَآءِ"),
            listOf("ٱلْحَمْدُ", "لِلَّهِ", "رَبِّ", "ٱلْعَٰلَمِينَ")
        )
    )

    @Test
    fun `typed digits and letters compare like the Mushaf's`() {
        assertEquals("2:255", SearchText.digits("٢:٢٥٥"))
        assertEquals("12", SearchText.digits("۱۲"))
        assertEquals("يايها", SearchText.normalize("يَـٰٓأَيُّهَا"))
        assertEquals("غفور رحيم", SearchText.normalize("  غفور   رحيم "))
        assertEquals("يايهالذين", SearchText.joined("يا ايها الذين"))
    }

    @Test
    fun `finds the words and says which ones matched`() {
        assertEquals(listOf(VerseMatch(1, listOf(3..4))), index.find("غفور رحيم"))
        // Inside a word, the whole word is marked.
        assertEquals(listOf(VerseMatch(1, listOf(4..4))), index.find("حيم"))
        // Every place in the verse, not just the first.
        assertEquals(listOf(VerseMatch(3, listOf(1..1, 5..5))), index.find("فباي"))
    }

    @Test
    fun `finds words the Mushaf joins when typed apart, and the other way round`() {
        assertEquals(listOf(VerseMatch(2, listOf(1..3))), index.find("يا أيها الذين آمنوا"))
        assertEquals(listOf(VerseMatch(4, listOf(1..2))), index.find("الحمدلله"))
        // Found as typed: no need to look again.
        assertEquals(listOf(VerseMatch(2, listOf(2..3))), index.find("الذين امنوا"))
    }

    @Test
    fun `keeps to the verses let through and needs two letters`() {
        assertEquals(listOf(1, 2), index.find("الله").map { it.ayahId })
        assertEquals(listOf(2), index.find("الله") { it == 2 }.map { it.ayahId })
        assertTrue(index.find("ا").isEmpty())
        assertTrue(index.find("   ").isEmpty())
    }

    @Test
    fun `overlapping and touching ranges become one`() {
        assertEquals(listOf(1..3, 5..5), VerseIndex.merge(listOf(2..3, 1..2, 5..5)))
        assertEquals(listOf(1..2), VerseIndex.merge(listOf(1..1, 2..2)))
    }

    @Test
    fun `a long verse is cut around its first match`() {
        assertEquals(0..9, MatchWindow.of(10, listOf(4..5)))
        // A few words over the limit are still shown whole.
        assertEquals(0..18, MatchWindow.of(19, listOf(18..19)))
        assertEquals(24..39, MatchWindow.of(40, listOf(30..31)))
        assertEquals(0..15, MatchWindow.of(40, listOf(2..2)))
        assertEquals(25..40, MatchWindow.of(60, listOf(30..30)))
    }

    private val verses = mapOf(1 to 7, 2 to 286, 3 to 200, 38 to 88, 50 to 45, 112 to 4)
    private val surahs = SurahData.ALL.map { SurahName(it.id, it.nameArabic, it.nameEnglish, verses[it.id] ?: 50) }
    private fun goTo(q: String) = SearchQuery.goTo(q, surahs)

    @Test
    fun `a number is a page, a surah or a juz`() {
        assertEquals(listOf(GoTo.Page(50), GoTo.Surah(50)), goTo("50"))
        assertEquals(listOf(GoTo.Page(3), GoTo.Surah(3), GoTo.Juz(3)), goTo("٣"))
        assertEquals(listOf(GoTo.Page(300)), goTo("300"))
        assertTrue(goTo("700").isEmpty())
    }

    @Test
    fun `a reference is a verse, by number or by name`() {
        val ayatAlKursi = listOf(GoTo.Verse(2, 255))
        assertEquals(ayatAlKursi, goTo("2:255"))
        assertEquals(ayatAlKursi, goTo("٢:٢٥٥"))
        assertEquals(ayatAlKursi, goTo("2 255"))
        assertEquals(ayatAlKursi, goTo("البقرة 255"))
        assertEquals(ayatAlKursi, goTo("بقرة ٢٥٥"))
        assertEquals(ayatAlKursi, goTo("سورة البقرة 255"))
        assertEquals(ayatAlKursi, goTo("Baqarah 255"))
        assertEquals(listOf(GoTo.Verse(3, 5)), goTo("عمران 5"))
        // A verse the surah does not have: the surah.
        assertEquals(listOf(GoTo.Surah(2)), goTo("2:300"))
    }

    @Test
    fun `a word and a number name a page, a juz or a surah`() {
        assertEquals(listOf(GoTo.Page(50)), goTo("صفحة 50"))
        assertEquals(listOf(GoTo.Juz(3)), goTo("الجزء ٣"))
        assertEquals(listOf(GoTo.Surah(18)), goTo("سورة 18"))
        assertEquals(listOf(GoTo.Page(12)), goTo("page 12"))
        assertTrue(goTo("juz 31").isEmpty())
    }

    @Test
    fun `a name finds its surah first`() {
        assertEquals(GoTo.Surah(2), goTo("البقرة").first())
        assertEquals(GoTo.Surah(38), goTo("ص").first())
        assertEquals(GoTo.Surah(112), goTo("الإخلاص").first())
        assertEquals(GoTo.Surah(3), goTo("آل عمران").first())
        assertEquals(GoTo.Surah(1), goTo("fatiha").first())
        assertTrue(goTo("غفور رحيم").isEmpty())
    }
}
