package com.example.mutqan.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LanternsGameTest {

    private fun w(id: Int, key: String) = GameWord(id, key)

    /** Two verses of four words; the wrong words are a fixed set none of the verses uses. */
    private class FakeSource : GameSource {
        val verses = listOf(
            GameVerse(1, listOf(GameWord(11, "a"), GameWord(12, "b"), GameWord(13, "c"), GameWord(14, "d")), given = 1),
            GameVerse(2, listOf(GameWord(21, "e"), GameWord(22, "f"), GameWord(23, "g"), GameWord(24, "h")), given = 1)
        )
        private var next = 0
        override fun nextVerse(): GameVerse? = verses.getOrNull(next++)
        override fun decoys(verse: GameVerse, right: GameWord, count: Int): List<GameWord> =
            listOf(GameWord(91, "x"), GameWord(92, "y"), GameWord(93, "z"), GameWord(94, "q")).filter { it.key != right.key }
    }

    private fun game(seed: Int = 1) = LanternsGame(FakeSource(), Random(seed)).also { it.start() }

    /** Runs the game until a rising lantern carries the word needed now, and returns it. */
    private fun LanternsGame.waitForNeeded(): Lantern {
        repeat(4000) {
            lanterns.firstOrNull { it.state == LanternState.RISING && it.word?.key == needed?.key && it.y < 0.95f }?.let { return it }
            update(0.02f)
        }
        error("the needed word never came")
    }

    private fun LanternsGame.waitForDecoy(): Lantern {
        repeat(4000) {
            lanterns.firstOrNull { it.state == LanternState.RISING && it.kind == LanternKind.WORD && it.word?.key != needed?.key }?.let { return it }
            update(0.02f)
        }
        error("no wrong word came")
    }

    @Test
    fun aRoundStartsWithTheGivenWordsInPlace() {
        val g = game()
        assertEquals(1, g.verse?.ayahId)
        assertEquals(1, g.placed)
        assertEquals(w(12, "b"), g.needed)
        assertEquals(LanternsGame.START_LIVES, g.lives)
    }

    @Test
    fun theNeededWordAlwaysComesUp() {
        val g = game(seed = 5)
        assertNotNull(g.waitForNeeded())
    }

    @Test
    fun theNeededWordComesSoonAfterItFloatsAway() {
        val g = game(seed = 3)
        val first = g.waitForNeeded()
        // Let it go: from the moment it is gone, the right word is back in the sky within a few seconds.
        repeat(3000) { if (first in g.lanterns) g.update(0.02f) }
        var waited = 0f
        while (g.lanterns.none { it.state == LanternState.RISING && it.word?.key == g.needed?.key } && waited < 4f) {
            g.update(0.02f)
            waited += 0.02f
        }
        assertTrue("waited $waited s for the needed word", waited < 4f)
    }

    @Test
    fun theRightLanternScoresAndPlacesTheWord() {
        val g = game()
        g.tap(g.waitForNeeded().id)
        assertEquals(LanternsGame.WORD_POINTS, g.score)
        assertEquals(1, g.combo)
        assertEquals(2, g.placed)
        assertTrue(g.events.any { it is GameEvent.Right })
    }

    @Test
    fun aWrongLanternCostsAHeartAndTheRun() {
        val g = game()
        g.tap(g.waitForNeeded().id)
        g.tap(g.waitForDecoy().id)
        assertEquals(LanternsGame.START_LIVES - 1, g.lives)
        assertEquals(0, g.combo)
        assertTrue(g.events.any { it is GameEvent.Wrong })
    }

    @Test
    fun theNeededWordFloatingAwayCostsAHeart() {
        val g = game()
        val lantern = g.waitForNeeded()
        repeat(2000) {
            if (lantern !in g.lanterns) return@repeat
            g.update(0.02f)
        }
        assertTrue(g.events.any { it is GameEvent.Missed })
        assertTrue(g.lives < LanternsGame.START_LIVES)
    }

    @Test
    fun aWholeVerseWithoutMistakesScoresDoubleAndRaisesTheLevel() {
        val g = game()
        repeat(3) { g.tap(g.waitForNeeded().id) }
        val done = g.events.filterIsInstance<GameEvent.VerseDone>().single()
        assertTrue(done.perfect)
        assertEquals((LanternsGame.VERSE_POINTS + LanternsGame.WORD_BONUS * 3) * 2, done.bonus)
        assertEquals(2, g.level)
        assertEquals(1, g.versesDone)
        // The next verse comes after the celebration.
        repeat(200) { g.update(0.02f) }
        assertEquals(2, g.verse?.ayahId)
        assertEquals(1, g.placed)
    }

    @Test
    fun threeMistakesEndTheRound() {
        val g = game()
        repeat(3) { if (!g.over) g.tap(g.waitForDecoy().id) }
        assertTrue(g.over)
        assertEquals(0, g.lives)
        assertTrue(g.events.any { it == GameEvent.Over })
        assertFalse(g.played.single().done)
    }

    @Test
    fun theRunMultipliesPointsUpToFive() {
        assertEquals(1, LanternsGame.multiplierFor(0))
        assertEquals(1, LanternsGame.multiplierFor(3))
        assertEquals(2, LanternsGame.multiplierFor(4))
        assertEquals(5, LanternsGame.multiplierFor(16))
        assertEquals(5, LanternsGame.multiplierFor(99))
    }

    @Test
    fun lanternsSideBySideDoNotStartOnTopOfEachOther() {
        val g = game(seed = 9)
        repeat(1500) {
            g.update(0.02f)
            val rising = g.lanterns.filter { it.state == LanternState.RISING }
            for (a in rising) for (b in rising) {
                if (a === b || kotlin.math.abs(a.lane - b.lane) > 1) continue
                assertTrue("lanterns ${a.id} and ${b.id} overlap", kotlin.math.abs(a.y - b.y) >= g.lanternHeight * 1.0f)
            }
        }
    }
}
