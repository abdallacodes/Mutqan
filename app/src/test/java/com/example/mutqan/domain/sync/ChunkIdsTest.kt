package com.example.mutqan.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChunkIdsTest {
    @Test
    fun idsAreAsciiWhateverTheLanguage() {
        val before = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-SA"))
            assertEquals("0000", ChunkIds.of(0))
            assertEquals("0012", ChunkIds.of(12))
        } finally {
            java.util.Locale.setDefault(before)
        }
    }

    @Test
    fun indexReadsAnyDigits() {
        assertEquals(0, ChunkIds.indexOf("0000"))
        assertEquals(7, ChunkIds.indexOf("0007"))
        assertEquals(0, ChunkIds.indexOf("٠٠٠٠"))
        assertEquals(12, ChunkIds.indexOf("٠٠١٢"))
        assertEquals(3, ChunkIds.indexOf("۰۰۰۳"))
        assertNull(ChunkIds.indexOf(""))
        assertNull(ChunkIds.indexOf("00a1"))
        assertNull(ChunkIds.indexOf("meta"))
    }

    @Test
    fun legacySpellingsAreTheOtherDigits() {
        assertEquals(listOf("٠٠٠١", "۰۰۰۱"), ChunkIds.legacy(1))
        assertEquals("٠٠١٢", ChunkIds.legacy(12).first())
    }

    @Test
    fun assembleReadsAnOldArabicIndicCopy() {
        val found = mapOf("٠٠٠٠" to "a", "٠٠٠١" to "b")
        assertEquals(listOf("a", "b"), ChunkIds.assemble(2, found))
    }

    @Test
    fun assembleReadsAnAsciiCopyAndPrefersItOverAnOldOne() {
        assertEquals(listOf("a", "b"), ChunkIds.assemble(2, mapOf("0001" to "b", "0000" to "a")))
        val both = mapOf("٠٠٠٠" to "stale", "0000" to "new")
        assertEquals(listOf("new"), ChunkIds.assemble(1, both))
    }

    @Test
    fun assembleSaysNullWhenAPieceIsReallyMissing() {
        assertNull(ChunkIds.assemble(2, mapOf("0000" to "a")))
        assertNull(ChunkIds.assemble(1, emptyMap()))
        assertNull(ChunkIds.assemble(1, mapOf("note" to "x")))
    }
}
