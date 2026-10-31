package com.example.mutqan.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the bundled roots and lemmas against words whose roots are well known. */
@RunWith(AndroidJUnit4::class)
class MorphologyAssetTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theBasmalaHasItsKnownRoots() = runBlocking {
        val m = MorphologyLibrary.load(context)
        // بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ: roots سمو, اله, رحم, رحم
        val roots = m.wordsOfAyah(1, 1).map { it.root }
        assertEquals(listOf("سمو", "اله", "رحم", "رحم"), roots.map { m.rootLetters(it) })
    }

    @Test
    fun aRootCanBeFoundByItsLettersWithOrWithoutSpaces() = runBlocking {
        val m = MorphologyLibrary.load(context)
        val ids = m.findRoots("ك ت ب")
        assertEquals(1, ids.size)
        assertEquals(ids, m.findRoots("كتب"))
        val info = m.infoOfRoot(ids.single())
        assertTrue(info.words > 200 && info.verses > 150 && info.verses <= info.words)
        assertTrue(m.findRoots("xyz").isEmpty())
    }

    @Test
    fun occurrencesAreInReadingOrderAndAgreeWithThePerAyahLookup() = runBlocking {
        val m = MorphologyLibrary.load(context)
        val root = m.findRoots("رحم").single()
        val places = m.occurrencesOfRoot(root)
        assertEquals(places.sortedWith(compareBy({ it.surah }, { it.ayah }, { it.position })), places)
        val first = places.first()
        assertTrue(m.wordsOfAyah(first.surah, first.ayah).any { it.position == first.position && it.root == root })
    }
}
