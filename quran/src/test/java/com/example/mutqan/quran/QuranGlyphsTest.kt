package com.example.mutqan.quran

import org.junit.Assert.assertEquals
import org.junit.Test

class QuranGlyphsTest {

    @Test
    fun `a surah name sits at its number in the surah-name font`() {
        assertEquals("", QuranGlyphs.surahName(1).text)
        assertEquals("", QuranGlyphs.surahName(20).text)
        assertEquals("", QuranGlyphs.surahName(114).text)
        assertEquals(R.font.surah_name_v4, QuranGlyphs.surahName(2).font)
    }

    @Test
    fun `a juz sits at its number, by number and by its opening words`() {
        assertEquals("", QuranGlyphs.juzNumber(1).text)
        assertEquals("", QuranGlyphs.juzNumber(30).text)
        assertEquals("", QuranGlyphs.juzName(1).text)
        assertEquals("", QuranGlyphs.juzName(30).text)
        assertEquals(R.font.quran_common, QuranGlyphs.juzNumber(5).font)
    }

    @Test
    fun `ink is measured from the baseline`() {
        val name = QuranGlyphs.surahName(2)
        assertEquals(1024f, name.inkUnits, 0.01f)
        assertEquals(370f, name.centerUnits, 0.01f)
        assertEquals(320f, QuranGlyphs.BANNER.centerUnits, 0.01f)
    }
}
