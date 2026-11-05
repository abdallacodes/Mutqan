package com.example.mutqan.quran

import org.junit.Assert.assertEquals
import org.junit.Test

class MushafHeaderTest {

    @Test
    fun `page numbers use Arabic-Indic digits`() {
        assertEquals("٢٤٨", MushafHeader.digits(248))
        assertEquals("١", MushafHeader.digits(1))
    }
}
