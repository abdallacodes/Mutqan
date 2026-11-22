package com.example.mutqan.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LanternsLevelsTest {

    private val levels = (1..120).map(LanternsLevels::rules)

    @Test
    fun everyDialOnlyEverTurnsTowardHarder() {
        for ((a, b) in levels.zipWithNext()) {
            val at = "level ${b.number}"
            assertTrue("$at: faster", b.dials.speed > a.dials.speed)
            assertTrue("$at: lanterns come more often", b.dials.interval < a.dials.interval)
            assertTrue("$at: helpers come more rarely", b.dials.helperEvery > a.dials.helperEvery)
            assertTrue("$at: the hint shows for less time", b.hintTime < a.hintTime)
            assertTrue("$at: lanterns in the sky", b.dials.sky >= a.dials.sky)
            assertTrue("$at: words to build", b.words >= a.words)
            assertTrue("$at: words given", b.given <= a.given)
            assertTrue("$at: longest verse", b.longestVerse >= a.longestVerse)
            assertTrue("$at: look-alike words", b.lookAlike >= a.lookAlike)
            assertTrue("$at: wind", b.dials.wind >= a.dials.wind)
        }
    }

    @Test
    fun theBigStepsComeOneALevelWhereTheyWereSaid() {
        fun steps(a: LevelRules, b: LevelRules) = listOf(
            (b.given < a.given),
            (b.dials.sky > a.dials.sky),
            a.lookAlike == 0f && b.lookAlike > 0f,
            a.dials.wind == 0f && b.dials.wind > 0f,
            a.dials.golden == 0f && b.dials.golden > 0f
        ).count { it }
        val stepAt = levels.zipWithNext().filter { (a, b) -> steps(a, b) > 0 }.map { (a, b) ->
            assertEquals("one step at level ${b.number}", 1, steps(a, b))
            b.number
        }
        assertEquals(listOf(5, 8, 10, 15, 18, 20, 25, 30), stepAt)
        assertEquals(LevelNews.LOOK_ALIKE, LanternsLevels.news(10))
        assertEquals(LevelNews.NONE_GIVEN, LanternsLevels.news(25))
        assertEquals(LevelNews.FASTER_LONGER, LanternsLevels.news(2))
        assertEquals(LevelNews.FASTER, LanternsLevels.news(40))
    }

    @Test
    fun theLevelsStartGentleAndStayPlayable() {
        val first = LanternsLevels.rules(1)
        assertEquals(LanternsLevels.FIRST_WORDS, first.words)
        assertEquals(2, first.given)
        assertEquals(3, first.dials.sky)
        assertTrue(first.dials.helpersAtOnce)
        val late = LanternsLevels.rules(120)
        assertEquals(LanternsLevels.MOST_WORDS, late.words)
        assertTrue(late.dials.speed < 0.21f)
        assertTrue(late.dials.interval > 0.62f)
        assertEquals(6, late.dials.sky)
        assertTrue(LanternsLevels.dawn(120) < 0.85f)
        assertEquals(0f, LanternsLevels.dawn(1))
    }

    @Test
    fun starsGoBySlips() {
        assertEquals(3, LanternsLevels.stars(0))
        assertEquals(2, LanternsLevels.stars(1))
        assertEquals(1, LanternsLevels.stars(2))
        assertEquals(1, LanternsLevels.stars(9))
    }

    @Test
    fun theBestStarsOfEachLevelAreKept() {
        val kept = LanternsLevels.parseStars("312")
        assertEquals(listOf(3, 1, 2), kept)
        assertEquals(listOf(3, 3, 2), LanternsLevels.withStars(kept, 2, 3))
        assertEquals(listOf(3, 1, 2), LanternsLevels.withStars(kept, 1, 1))
        assertEquals(listOf(3, 1, 2, 2), LanternsLevels.withStars(kept, 4, 2))
        // Past the next level (a test build plays any level): not kept.
        assertEquals(listOf(3, 1, 2), LanternsLevels.withStars(kept, 9, 3))
        assertEquals("3122", LanternsLevels.formatStars(listOf(3, 1, 2, 2)))
        assertEquals(emptyList<Int>(), LanternsLevels.parseStars(null))
    }
}

class LevelVersesTest {

    private fun candidates(random: Random, count: Int = 80, longest: Int = 14) =
        List(count) { VerseLength(1000 + it, 3 + random.nextInt(longest - 2)) }

    @Test
    fun theWordsToBuildAddUpToTheLevel() {
        val random = Random(4)
        for (level in listOf(1, 4, 5, 12, 24, 25, 40)) {
            val rules = LanternsLevels.rules(level)
            repeat(20) {
                val pool = candidates(random, longest = rules.longestVerse)
                val picked = LevelVerses.pick(pool, rules.words, rules.given, emptyList(), random)
                val lengths = pool.associate { it.ayahId to it.words }
                assertEquals("level $level", rules.words, picked.sumOf { lengths.getValue(it) - rules.given })
                assertEquals("each verse once", picked.size, picked.toSet().size)
            }
        }
    }

    @Test
    fun slippedVersesComeFirstUpToHalfTheWords() {
        val pool = (1..40).map { VerseLength(it, 5) }
        val slipped = listOf(7, 3, 30, 22, 9, 11, 14)
        val picked = LevelVerses.pick(pool, target = 24, given = 1, slipped = slipped, random = Random(1))
        // 4 words each: three slipped verses (12 words, half), then others.
        assertEquals(6, picked.size)
        assertEquals(setOf(7, 3, 30), picked.filter { it in slipped }.toSet())
    }

    @Test
    fun aSmallHifzComesAgainToFillTheLevel() {
        val pool = listOf(VerseLength(1, 6), VerseLength(2, 7))
        val picked = LevelVerses.pick(pool, target = 20, given = 1, slipped = emptyList(), random = Random(3))
        val lengths = pool.associate { it.ayahId to it.words }
        assertTrue(picked.size > 2)
        assertTrue(picked.sumOf { lengths.getValue(it) - 1 } >= 18)
    }

    @Test
    fun versesWithTooLittleToBuildAreLeftOut() {
        val pool = listOf(VerseLength(1, 3), VerseLength(2, 6))
        val picked = LevelVerses.pick(pool, target = 4, given = 2, slipped = emptyList(), random = Random(1))
        assertEquals(listOf(2), picked)
        assertEquals(emptyList<Int>(), LevelVerses.pick(listOf(VerseLength(1, 3)), 10, 2, emptyList(), Random(1)))
    }
}

class GameSlipsTest {

    @Test
    fun aSlipPutsTheVerseFirstNeedingTwoCleanBuilds() {
        val before = GameSlips.parse("5:1,9:2")
        val after = GameSlips.after(before, listOf(PlayedVerse(9, mistakes = 1, done = true)))
        assertEquals(listOf(9, 5), after.keys.toList())
        assertEquals(GameSlips.CLEAN_TO_LEAVE, after[9])
    }

    @Test
    fun cleanBuildsCountOffUntilTheVerseLeaves() {
        var slips = GameSlips.parse("4:2")
        slips = GameSlips.after(slips, listOf(PlayedVerse(4, 0, done = true)))
        assertEquals(1, slips[4])
        slips = GameSlips.after(slips, listOf(PlayedVerse(4, 0, done = true)))
        assertTrue(slips.isEmpty())
    }

    @Test
    fun aVerseLeftUnfinishedIsASlip() {
        val after = GameSlips.after(emptyMap(), listOf(PlayedVerse(12, mistakes = 0, done = false)))
        assertEquals(mapOf(12 to 2), after)
    }

    @Test
    fun theListKeepsItsShapeAndSize() {
        val many = GameSlips.after(emptyMap(), (1..100).map { PlayedVerse(it, 1, true) })
        assertEquals(GameSlips.MOST, many.size)
        // The most recent first.
        assertEquals(100, many.keys.first())
        assertEquals(many, GameSlips.parse(GameSlips.format(many)))
        assertEquals(mapOf(3 to 2), GameSlips.parse("3:7,bad,:1,3:1"))
    }
}

class NextWordsTest {

    /** Each letter a word of its own, in Arabic letters (the index compares Arabic letters only). */
    private fun words(vararg verses: String): List<List<String>> {
        val letters = "بتثجحخدذرزسشصض"
        return verses.map { verse -> verse.split(' ').map { w -> w.map { letters["abcdefgkqxzmnp".indexOf(it)] }.joinToString("") } }
    }

    @Test
    fun findsTheWordThatFollowsTheSameTwoWordsElsewhere() {
        val next = NextWords.build(words("a b c d", "x a b e", "a b c f", "q a b g"))
        // Word 2 of ayah 1 is "c": after "a b", ayah 2 has "e" and ayah 4 "g"; ayah 3 has "c" too, so not a look-alike.
        assertEquals(setOf(NextWords.Place(2, 3), NextWords.Place(4, 3)), next.lookAlikes(1, 2, 5).toSet())
        assertEquals(1, next.lookAlikes(1, 2, 1).size)
        // Word 3 ("d"), after "b c": ayah 3 goes on with "f".
        assertEquals(listOf(NextWords.Place(3, 3)), next.lookAlikes(1, 3, 5))
        assertTrue(next.lookAlikes(1, 1, 5).isEmpty())
    }

    @Test
    fun threeWordsMatchingComeBeforeTwo() {
        val next = NextWords.build(words("k a b c", "z a b d", "k a b e"))
        assertEquals(listOf(NextWords.Place(3, 3), NextWords.Place(2, 3)), next.lookAlikes(1, 3, 5))
    }

    @Test
    fun wordsAreComparedByTheirLetters() {
        assertEquals(NextWords.letters("الرحيم"), NextWords.letters("ٱلرَّحِيمِ"))
        assertEquals(NextWords.letters("الصلاه"), NextWords.letters("ٱلصَّلَاةَ"))
        assertEquals("", NextWords.letters("١"))
    }
}
