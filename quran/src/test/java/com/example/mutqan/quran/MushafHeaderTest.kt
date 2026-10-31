package com.example.mutqan.quran

import org.junit.Assert.assertEquals
import org.junit.Test

class MushafHeaderTest {

    @Test
    fun `juz titles are spelled as in a printed mushaf`() {
        assertEquals("الجزء الأول", MushafHeader.juzTitle(1))
        assertEquals("الجزء الثالث عشر", MushafHeader.juzTitle(13))
        assertEquals("الجزء الحادي والعشرون", MushafHeader.juzTitle(21))
        assertEquals("الجزء الثلاثون", MushafHeader.juzTitle(30))
    }

    @Test
    fun `page numbers use Arabic-Indic digits`() {
        assertEquals("٢٤٨", MushafHeader.digits(248))
        assertEquals("١", MushafHeader.digits(1))
    }
}
