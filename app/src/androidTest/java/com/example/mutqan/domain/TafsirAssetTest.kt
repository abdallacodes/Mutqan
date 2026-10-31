package com.example.mutqan.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the bundled tafsir database: every verse has text in every book, and Saadi quotes the verse it explains. */
@RunWith(AndroidJUnit4::class)
class TafsirAssetTest {

    private val library = TafsirLibrary.get(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun everyVerseHasTextInEveryBook() {
        for (source in TafsirSource.entries) {
            val missing = (1..6236).filter { library.entryFor(source, it) == null }
            assertEquals("$source has no text for $missing", 0, missing.size)
        }
    }

    @Test
    fun anEntryCoversTheVerseItWasAskedFor() {
        for (id in listOf(1, 7, 8, 300, 6236)) {
            val entry = library.entryFor(TafsirSource.SAADI, id)!!
            assertTrue(id in entry.fromId..entry.toId)
        }
    }

    @Test
    fun saadiExplainsTheFatihaAsOneEntryAndQuotesTheBasmala() {
        val entry = library.entryFor(TafsirSource.SAADI, 3)!!
        assertEquals(1, entry.fromId)
        assertEquals(7, entry.toId)
        val blocks = TafsirText.parse(entry.body)
        assertTrue(blocks.any { block -> block.runs.any { it.quote } })
    }

    @Test
    fun anIdOutsideTheQuranHasNothing() {
        assertEquals(null, library.entryFor(TafsirSource.SAADI, 0))
        assertEquals(null, library.entryFor(TafsirSource.SAADI, 7000))
        assertNotNull(library.entryFor(TafsirSource.IRAB, 1))
    }
}
