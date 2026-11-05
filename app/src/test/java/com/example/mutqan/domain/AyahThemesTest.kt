package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AyahThemesTest {

    private val index = AyahThemeIndex(
        listOf(
            AyahTheme(1, 7, "Supplication to Allah for guidance"),
            AyahTheme(8, 12, "The Book is a guide for the God-conscious"),
            AyahTheme(15, 23, "Hypocrites and the consequences of hypocrisy")
        )
    )

    @Test
    fun `an ayah is found in the passage that holds it`() {
        assertEquals(1, index.themeOf(1)?.first)
        assertEquals(1, index.themeOf(7)?.first)
        assertEquals(8, index.themeOf(8)?.first)
        assertEquals(15, index.themeOf(23)?.first)
    }

    @Test
    fun `an ayah between two passages, or outside them, has no theme`() {
        assertNull(index.themeOf(13))
        assertNull(index.themeOf(14))
        assertNull(index.themeOf(24))
        assertNull(index.themeOf(0))
    }

    @Test
    fun `a search needs every word, in any case and any order`() {
        assertEquals(listOf(8), index.search("guide book").map { it.first })
        assertEquals(listOf(15), index.search("HYPOCRISY").map { it.first })
        assertEquals(emptyList<Int>(), index.search("guidance hypocrites"))
    }

    @Test
    fun `an empty search lists every passage`() {
        assertEquals(3, index.search("  ").size)
    }
}
