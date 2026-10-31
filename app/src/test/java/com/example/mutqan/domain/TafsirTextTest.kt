package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TafsirTextTest {

    @Test
    fun `paragraphs are split on newlines and empty ones dropped`() {
        val blocks = TafsirText.parse("first\n\n  second  \n")
        assertEquals(listOf("first", "second"), blocks.map { it.runs.single().text })
    }

    @Test
    fun `a quotation is marked inside a paragraph`() {
        val block = TafsirText.parse("he said \u0001﴿a b﴾\u0002 then")[0]
        assertEquals(listOf("he said ", "﴿a b﴾", " then"), block.runs.map { it.text })
        assertEquals(listOf(false, true, false), block.runs.map { it.quote })
    }

    @Test
    fun `a short line ending with a colon is a heading`() {
        val blocks = TafsirText.parse("meaning of the verse:\nthe verse means this: and that")
        assertTrue(blocks[0].heading)
        assertFalse(blocks[1].heading)
    }

    @Test
    fun `a quotation alone is not a heading`() {
        assertFalse(TafsirText.parse("\u0001word:\u0002")[0].heading)
    }

    @Test
    fun `source ids are stable and unknown ids fall back to Saadi`() {
        assertEquals(listOf(1, 2, 3, 4), TafsirSource.entries.map { it.id })
        assertEquals(TafsirSource.IRAB, TafsirSource.fromId(4))
        assertEquals(TafsirSource.SAADI, TafsirSource.fromId(99))
    }

    @Test
    fun `an entry covering several verses says so`() {
        assertTrue(TafsirEntry(5, 9, "x").coversSeveral)
        assertFalse(TafsirEntry(5, 5, "x").coversSeveral)
    }
}
