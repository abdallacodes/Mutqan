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
        val asked = mutableListOf<Int>()
        override fun decoys(verse: GameVerse, at: Int, count: Int): List<GameWord> {
            asked += at
            return listOf(GameWord(91, "x"), GameWord(92, "y"), GameWord(93, "z"), GameWord(94, "q")).filter { it.key != verse.words[at].key }
        }
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

    private val levelDials = Dials(speed = 0.1f, interval = 1f, sky = 3, helperEvery = 30f, helpersAtOnce = true)

    private fun level(dials: Dials = levelDials, seed: Int = 1) = LanternsGame(FakeSource(), Random(seed), dials).also { it.start() }

    @Test
    fun aLevelIsWonWhenItsLastVerseIsBuilt() {
        val g = level()
        repeat(3) { g.tap(g.waitForNeeded().id) }
        repeat(200) { g.update(0.02f) }
        assertEquals(2, g.verse?.ayahId)
        repeat(3) { g.tap(g.waitForNeeded().id) }
        repeat(200) { g.update(0.02f) }
        assertTrue(g.over)
        assertTrue(g.cleared)
        assertEquals(6, g.built)
        assertEquals(0, g.slips)
        // A level keeps its dials: no level up after a verse.
        assertTrue(g.events.none { it is GameEvent.LevelUp })
        assertEquals(1, g.level)
    }

    @Test
    fun aLevelLostIsNotCleared() {
        val g = level()
        repeat(3) { if (!g.over) g.tap(g.waitForDecoy().id) }
        assertTrue(g.over)
        assertFalse(g.cleared)
        assertEquals(3, g.slips)
    }

    @Test
    fun slipsCountWrongLanternsAndWordsThatFloatAway() {
        val g = level()
        g.tap(g.waitForDecoy().id)
        val lantern = g.waitForNeeded()
        repeat(2000) { if (lantern in g.lanterns) g.update(0.02f) }
        assertEquals(1, g.wrong)
        assertEquals(1, g.missed)
        assertEquals(2, g.slips)
    }

    @Test
    fun aGoldenLanternTriplesTheWordsPoints() {
        val g = level(levelDials.copy(golden = 1f))
        val lantern = g.waitForNeeded()
        assertTrue(lantern.golden)
        g.tap(lantern.id)
        assertEquals(LanternsGame.WORD_POINTS * LanternsGame.GOLDEN_TIMES, g.score)
    }

    @Test
    fun inALevelTheHelpersComeFromTheStart() {
        val g = level(levelDials.copy(helperEvery = 2f))
        var seen = false
        repeat(300) {
            g.update(0.02f)
            if (g.lanterns.any { it.kind != LanternKind.WORD }) seen = true
        }
        assertTrue("no helper lantern in the first six seconds", seen)
    }

    /** The word wanted is أَنَّ; beside it float إِنَّ (hamza below), أُنَّ (another vowel) and أَنَّ with only a stop sign more. */
    private class NearSource : GameSource {
        private val verse = GameVerse(
            1,
            listOf(GameWord(11, WordKey.of("قَالَ")), GameWord(12, WordKey.of("أَنَّ")), GameWord(13, WordKey.of("ٱللَّهَ"))),
            given = 1
        )
        private var sent = false
        override fun nextVerse(): GameVerse? = if (sent) null else verse.also { sent = true }
        override fun decoys(verse: GameVerse, at: Int, count: Int) =
            listOf(GameWord(91, WordKey.of("إِنَّ")), GameWord(92, WordKey.of("أُنَّ")), GameWord(93, WordKey.of("أَنَّۖ")))
    }

    @Test
    fun aWordThatDiffersOnlyByTheHamzaOrAVowelIsWrong() {
        val g = LanternsGame(NearSource(), Random(4), levelDials).also { it.start() }
        val near = g.waitForDecoy()
        assertTrue("a word like the right one, not the right one: ${near.word}", near.word!!.id in listOf(91, 92))
        g.tap(near.id)
        assertEquals(1, g.wrong)
        assertEquals(1, g.placed)
        assertEquals(LanternsGame.START_LIVES - 1, g.lives)
        // The word with the same letters and marks is the right one (a stop sign is no part of the word).
        g.tap(g.waitForNeeded().id)
        assertEquals(2, g.placed)
    }

    private fun rising(g: LanternsGame) = g.lanterns.filter { it.state == LanternState.RISING && it.kind == LanternKind.WORD }

    @Test
    fun theWordAfterTheWantedOneIsAlreadyUp() {
        val g = LanternsGame(FakeSource(), Random(3), levelDials.copy(ahead = 1)).also { it.start() }
        val first = g.waitForNeeded()
        // Within moments the word after it is up too, before it is wanted.
        var waited = 0f
        while (rising(g).none { it.word?.key == "c" } && waited < 3f) { g.update(0.02f); waited += 0.02f }
        assertTrue("the next word took $waited s to come up", waited < 3f)
        g.tap(first.id)
        // So once the wanted word is tapped, the next is there at once.
        assertTrue(rising(g).any { it.word?.key == g.needed?.key })
    }

    @Test
    fun aWordTappedBeforeItsTurnIsASlip() {
        val g = LanternsGame(FakeSource(), Random(3), levelDials.copy(ahead = 1)).also { it.start() }
        var early: Lantern? = null
        repeat(400) { if (early == null) { g.update(0.02f); early = rising(g).firstOrNull { it.word?.key == "c" && it.y < 0.95f } } }
        g.tap(early!!.id)
        assertEquals(1, g.wrong)
        assertEquals(1, g.placed)
    }

    @Test
    fun aCalmRoundLosesNoHeartAndWaitsAfterEachVerse() {
        val g = LanternsGame(FakeSource(), Random(2), levelDials, RoundRules(forgiving = true, waitAfterVerse = true)).also { it.start() }
        repeat(5) { if (!g.over) g.tap(g.waitForDecoy().id) }
        assertFalse(g.over)
        assertEquals(LanternsGame.START_LIVES, g.lives)
        assertEquals(5, g.slips)
        repeat(3) { g.tap(g.waitForNeeded().id) }
        assertTrue(g.waiting)
        // It waits, however long, until it is told to go on.
        repeat(500) { g.update(0.02f) }
        assertEquals(1, g.verse?.ayahId)
        g.proceed()
        assertFalse(g.waiting)
        assertEquals(2, g.verse?.ayahId)
    }

    @Test
    fun withOneHeartASingleSlipEndsTheRound() {
        val g = LanternsGame(FakeSource(), Random(2), levelDials, RoundRules(lives = 1)).also { it.start() }
        assertEquals(1, g.startLives)
        g.tap(g.waitForDecoy().id)
        assertTrue(g.over)
    }

    @Test
    fun aRunQuickensTheSkyAndASlipLetsItSettle() {
        val g = LanternsGame(FakeSource(), Random(2), levelDials.copy(flow = 0.05f, ahead = 1)).also { it.start() }
        repeat(3) { g.tap(g.waitForNeeded().id) }
        repeat(200) { g.update(0.02f) }
        repeat(2) { g.tap(g.waitForNeeded().id) }
        repeat(60) { g.update(0.02f) }
        assertTrue("tempo ${g.tempo} after a run of five", g.tempo > 1.15f)
        g.tap(g.waitForDecoy().id)
        repeat(150) { g.update(0.02f) }
        assertTrue("tempo ${g.tempo} after a slip", g.tempo < 1.05f)
    }

    /** Seconds until a lantern of the word wanted now has risen to where it can be read. */
    private fun LanternsGame.secondsUntilWantedReadable(): Float {
        var t = 0f
        while (t < 30f) {
            if (lanterns.any { it.state == LanternState.RISING && it.word?.key == needed?.key && it.y <= LanternsGame.READ_LINE }) return t
            update(0.02f)
            t += 0.02f
        }
        return t
    }

    private fun calm(dials: Dials = GameModes.calmDials, seed: Int = 4) =
        LanternsGame(FakeSource(), Random(seed), dials, GameModes.calmRules).also { it.start() }

    @Test
    fun theCalmSkyBringsTheWantedWordWithoutAWait() {
        for (seed in 1..6) {
            val brought = calm(seed = seed)
            val plain = calm(GameModes.calmDials.copy(catchUp = 1f), seed)
            val quick = brought.secondsUntilWantedReadable()
            val slow = plain.secondsUntilWantedReadable()
            assertTrue("seed $seed: the first word took $quick s (without bringing $slow s)", quick < 2.5f && quick < slow / 2)
            // And after each right word, the next is readable within moments.
            repeat(2) {
                brought.tap(brought.lanterns.first { it.state == LanternState.RISING && it.word?.key == brought.needed?.key && it.y <= LanternsGame.READ_LINE }.id)
                val next = brought.secondsUntilWantedReadable()
                assertTrue("seed $seed: the next word took $next s", next < 2.5f)
            }
        }
    }

    @Test
    fun onceTheWantedWordIsUpTheCalmSkySettles() {
        val g = calm()
        g.secondsUntilWantedReadable()
        repeat(150) { g.update(0.02f) }
        assertTrue("tempo ${g.tempo} with the wanted word up", g.tempo < 1.2f)
    }

    @Test
    fun theCalmSkyBringingWordsKeepsLanternsApart() {
        val g = calm(seed = 9)
        repeat(1500) { step ->
            g.update(0.02f)
            // Now and then a right word, so the sky keeps bringing the next one.
            if (step % 120 == 0) g.lanterns.firstOrNull { it.state == LanternState.RISING && it.word?.key == g.needed?.key && it.y < 0.95f }?.let { g.tap(it.id) }
            if (g.waiting) g.proceed()
            val rising = g.lanterns.filter { it.state == LanternState.RISING }
            for (a in rising) for (b in rising) {
                if (a === b || kotlin.math.abs(a.lane - b.lane) > 1) continue
                assertTrue("lanterns ${a.id} and ${b.id} overlap", kotlin.math.abs(a.y - b.y) >= g.lanternHeight * 1.0f)
            }
        }
    }

    @Test
    fun theWrongWordsAreAskedForTheWantedWordsPlace() {
        val source = FakeSource()
        val g = LanternsGame(source, Random(2), levelDials).also { it.start() }
        g.tap(g.waitForNeeded().id)
        repeat(300) { g.update(0.02f) }
        assertTrue(source.asked.isNotEmpty())
        assertTrue(source.asked.all { it in 1..3 })
        assertTrue(2 in source.asked)
    }
}
