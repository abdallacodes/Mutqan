package com.example.mutqan.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        assertTrue(late.dials.speed < 0.26f)
        assertTrue(late.dials.interval > 0.55f)
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

class LevelLeapTest {

    @Test
    fun threeStarsOnTheFurthestLevelOpenTwo() {
        assertEquals(1, LanternsLevels.furthestOpen(emptyList()))
        assertEquals(3, LanternsLevels.furthestOpen(listOf(3, 2)))
        assertEquals(4, LanternsLevels.furthestOpen(listOf(3, 3)))
        // Leapt over level 3: it stays open (no stars) and level 4 can be won.
        val leapt = LanternsLevels.withStars(listOf(3, 3), 4, 2)
        assertEquals(listOf(3, 3, 0, 2), leapt)
        assertEquals(5, LanternsLevels.furthestOpen(leapt))
        // Winning the one leapt over later fills it in.
        assertEquals(listOf(3, 3, 1, 2), LanternsLevels.withStars(leapt, 3, 1))
        // Beyond what is open: not kept.
        assertEquals(listOf(3, 2), LanternsLevels.withStars(listOf(3, 2), 5, 3))
    }

    @Test
    fun theLevelsKeepTheWantedWordAndTheNextUpAndQuickenWithARun() {
        assertEquals(1, LanternsLevels.rules(1).dials.ahead)
        assertEquals(1, LanternsLevels.rules(40).dials.ahead)
        assertTrue(LanternsLevels.rules(1).dials.flow > 0f)
        assertTrue(LanternsLevels.rules(1).dials.speed > 0.09f)
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
                val picked = LevelVerses.pick(pool, rules.words, rules.given, emptyList(), emptyList(), random)
                val lengths = pool.associate { it.ayahId to it.words }
                assertEquals("level $level", rules.words, picked.sumOf { lengths.getValue(it) - rules.given })
                assertEquals("each verse once", picked.size, picked.toSet().size)
            }
        }
    }

    @Test
    fun dueSlippedVersesComeFirstUpToAThirdOfTheWords() {
        val pool = (1..40).map { VerseLength(it, 5) }
        val slipped = listOf(7, 3, 30, 22, 9, 11, 14)
        val picked = LevelVerses.pick(pool, target = 24, given = 1, slipped = slipped, recent = emptyList(), random = Random(1))
        // 4 words each: two slipped verses (8 words, a third), then others.
        assertEquals(6, picked.size)
        assertEquals(setOf(7, 3), picked.filter { it in slipped }.toSet())
    }

    @Test
    fun aSlippedVerseWaitsItsTurnBeforeItComesBack() {
        val pool = (1..40).map { VerseLength(it, 5) }
        // Verse 7 was drawn just now (the third latest); verse 3 a long time ago.
        val recent = listOf(20, 21, 7) + (22..39).toList() + listOf(3)
        repeat(20) { seed ->
            val picked = LevelVerses.pick(pool, target = 12, given = 1, slipped = listOf(7, 3), recent = recent, random = Random(seed))
            assertTrue("7 is still cooling down: $picked", 7 !in picked)
            assertTrue("3 is due: $picked", 3 in picked)
        }
    }

    @Test
    fun versesDrawnLatelyWaitWhileOthersAreLeft() {
        val pool = (1..30).map { VerseLength(it, 5) }
        val recent = (1..20).toList()
        repeat(30) { seed ->
            val picked = LevelVerses.pick(pool, target = 16, given = 1, slipped = emptyList(), recent = recent, random = Random(seed))
            assertEquals(4, picked.size)
            assertTrue("only verses not drawn lately: $picked", picked.all { it > 20 })
        }
    }

    @Test
    fun whenAllWereDrawnTheOneDrawnLongestAgoComesFirst() {
        val pool = (1..6).map { VerseLength(it, 5) }
        // Latest first: 1 was drawn last, 6 the longest ago.
        val picked = LevelVerses.pick(pool, target = 8, given = 1, slipped = emptyList(), recent = listOf(1, 2, 3, 4, 5, 6), random = Random(2))
        assertEquals(setOf(6, 5), picked.toSet())
    }

    @Test
    fun versesWithTheSameWordsAreOneVerse() {
        // Ten ayahs say the same words (a refrain), the others are all different.
        val refrain = (100..109).map { VerseLength(it, 4, twin = 100) }
        val others = (1..8).map { VerseLength(it, 5) }
        repeat(30) { seed ->
            val picked = LevelVerses.pick(refrain + others, target = 16, given = 1, slipped = emptyList(), recent = emptyList(), random = Random(seed))
            assertTrue("the refrain once at most: $picked", picked.count { it >= 100 } <= 1)
        }
    }

    @Test
    fun theTwinPlayedIsTheSlippedOneOrElseTheOneNotDrawnLately() {
        val pool = (100..103).map { VerseLength(it, 5, twin = 100) } + (1..30).map { VerseLength(it, 5) }
        // 101 was slipped on, so it is the one that comes.
        repeat(10) { seed ->
            val picked = LevelVerses.pick(pool, target = 8, given = 1, slipped = listOf(101), recent = emptyList(), random = Random(seed))
            assertEquals(1, picked.count { it >= 100 })
            assertTrue(101 in picked)
        }
        // None slipped, 100 to 102 drawn lately: 103 stands for the group.
        val order = LevelVerses.order(pool, emptyList(), listOf(102, 101, 100), Random(5), maxSlipped = 3)
        assertEquals(1, order.count { it >= 100 })
        assertTrue(103 in order)
    }

    @Test
    fun theOpenRoundGoesFreshFirstAndSlipsAreFew() {
        val pool = (1..30).map { VerseLength(it, 6) }
        val order = LevelVerses.order(pool, slipped = (1..10).toList(), recent = (11..20).toList(), random = Random(9), maxSlipped = 3)
        assertEquals(30, order.size)
        assertEquals(30, order.toSet().size)
        // Slipped ones that are due: 1 to 3 first (the most recent slip first), then those not drawn lately (21 to 30,
        // and the due slips beyond three), the rest after: the one drawn longest ago first.
        assertEquals(listOf(1, 2, 3), order.take(3))
        assertEquals(((21..30) + (4..10)).toSet(), order.subList(3, 20).toSet())
        assertEquals(((11..20).toList()).reversed(), order.takeLast(10))
    }

    @Test
    fun aSmallHifzComesAgainToFillTheLevel() {
        val pool = listOf(VerseLength(1, 6), VerseLength(2, 7))
        val picked = LevelVerses.pick(pool, target = 20, given = 1, slipped = emptyList(), recent = emptyList(), random = Random(3))
        val lengths = pool.associate { it.ayahId to it.words }
        assertTrue(picked.size > 2)
        assertTrue(picked.sumOf { lengths.getValue(it) - 1 } >= 18)
    }

    @Test
    fun versesWithTooLittleToBuildAreLeftOut() {
        val pool = listOf(VerseLength(1, 3), VerseLength(2, 6))
        val picked = LevelVerses.pick(pool, target = 4, given = 2, slipped = emptyList(), recent = emptyList(), random = Random(1))
        assertEquals(listOf(2), picked)
        assertEquals(emptyList<Int>(), LevelVerses.pick(listOf(VerseLength(1, 3)), 10, 2, emptyList(), emptyList(), Random(1)))
    }

    @Test
    fun aSmallPoolIsWidenedWithTheShortestLongerVerses() {
        val reach = (1..5).map { VerseLength(it, 6) }
        val beyond = listOf(VerseLength(50, 12), VerseLength(51, 9), VerseLength(52, 10)) + (60..100).map { VerseLength(it, 11) }
        val pool = LevelVerses.withEnough(reach, beyond)
        assertEquals(LevelVerses.MIN_POOL, pool.map { it.twin }.toSet().size)
        // The shortest of the longer verses come first.
        assertTrue(pool.any { it.ayahId == 51 } && pool.any { it.ayahId == 52 } && pool.none { it.ayahId == 50 })
        // Enough already: left as it is.
        val big = (1..LevelVerses.MIN_POOL).map { VerseLength(it, 6) }
        assertEquals(big, LevelVerses.withEnough(big, beyond))
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

class GameSeenTest {

    @Test
    fun theLatestDrawnComeFirstEachVerseOnce() {
        assertEquals(listOf(4, 5, 1, 2, 3), GameSeen.after(listOf(1, 2, 3, 4), listOf(4, 5)))
        assertEquals(listOf(7, 8), GameSeen.after(emptyList(), listOf(7, 8, 7)))
    }

    @Test
    fun theListKeepsItsShapeAndSize() {
        val many = (1..GameSeen.MOST + 50).toList()
        assertEquals(GameSeen.MOST, GameSeen.after(emptyList(), many).size)
        assertEquals(listOf(5, 3, 9), GameSeen.parse(GameSeen.format(listOf(5, 3, 9))))
        assertEquals(listOf(3, 7), GameSeen.parse("3, 7,bad,-2,3"))
        assertEquals(emptyList<Int>(), GameSeen.parse(null))
    }
}

class NextWordsTest {

    /** Each letter a word of its own, in Arabic letters. */
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
    fun aWordThatDiffersOnlyInItsHamzaOrItsMarksIsAnotherWord() {
        val next = NextWords.build(
            listOf(
                listOf("قَالَ", "ٱللَّهُ", "أَنَّ"),
                listOf("قَالَ", "ٱللَّهُ", "إِنَّ"),
                listOf("قَالَ", "ٱللَّهُ", "أَنَّ"),
                listOf("قَالَ", "ٱللَّهُ", "أَنَّۚ"),
                listOf("قَالَ", "ٱللَّهُ", "أَنُّ")
            )
        )
        // After the same two words ayah 1 has أَنَّ: ayah 2 has إِنَّ and ayah 5 أَنُّ, both another word (a look-alike);
        // ayahs 3 and 4 have the same word (a stop sign is no part of it).
        assertEquals(setOf(NextWords.Place(2, 2), NextWords.Place(5, 2)), next.lookAlikes(1, 2, 5).toSet())
    }
}

class WordKeyTest {

    @Test
    fun hamzaAboveAndBelowAreTwoWords() {
        assertNotEquals(WordKey.of("أَنَّ"), WordKey.of("إِنَّ"))
        assertNotEquals(WordKey.of("أن"), WordKey.of("إن"))
        assertNotEquals(WordKey.of("أَن"), WordKey.of("ان"))
    }

    @Test
    fun marksMakeTwoWords() {
        assertNotEquals(WordKey.of("عَلِمَ"), WordKey.of("عُلِمَ"))
        assertNotEquals(WordKey.of("عَلَيۡهِمۡ"), WordKey.of("عَلَيۡهِمُ"))
        assertNotEquals(WordKey.of("رَبِّ"), WordKey.of("رَبَّ"))
        // The shadda and the vowel count, and the dagger alif.
        assertNotEquals(WordKey.of("مَدَّ"), WordKey.of("مَدَ"))
        assertNotEquals(WordKey.of("ٱلرَّحْمَٰنِ"), WordKey.of("ٱلرَّحْمَانِ"))
        // And the shape of the letters: alef maqsura, ya, ta marbuta, ha.
        assertNotEquals(WordKey.of("إِلَىٰ"), WordKey.of("إِلَيٰ"))
        assertNotEquals(WordKey.of("رَحْمَةٗ"), WordKey.of("رَحْمَهٗ"))
    }

    @Test
    fun theSameWordIsTheSameWordAnywhere() {
        assertEquals(WordKey.of("ٱلرَّحِيمِ"), WordKey.of("ٱلرَّحِيمِ"))
        // Stop signs, the sign of a section or of prostration, and the stretching line are no part of the word.
        assertEquals(WordKey.of("ٱلۡعَٰلَمِينَ"), WordKey.of("ٱلۡعَٰلَمِينَۖ"))
        assertEquals(WordKey.of("ٱلۡعَٰلَمِينَ"), WordKey.of("۞ ٱلۡعَٰلَمِينَ"))
        assertEquals(WordKey.of("يَسۡجُدُونَ"), WordKey.of("يَسۡجُدُونَ۩"))
        assertEquals(WordKey.of("ٱلنَّبِيِّۧنَ"), WordKey.of("ٱلنَّبِيِّـۧنَ"))
    }

    @Test
    fun theOpenTanweensAreThePlainOnes() {
        // Fathatan, dammatan and kasratan as the Mushaf writes them before some letters, and the plain ones at a pause.
        assertEquals(WordKey.of("هُدًى"), WordKey.of("هُدٗى"))
        assertEquals(WordKey.of("عَظِيمٌ"), WordKey.of("عَظِيمٞ"))
        assertEquals(WordKey.of("لِّقَوۡمٍ"), WordKey.of("لِّقَوۡمٖ"))
        // But the three are not one another.
        assertNotEquals(WordKey.of("عَظِيمٌ"), WordKey.of("عَظِيمٍ"))
        assertNotEquals(WordKey.of("عَظِيمٌ"), WordKey.of("عَظِيمً"))
        // The small meem of a run-on is no part of the word.
        assertEquals(WordKey.of("عَلِيمٌ"), WordKey.of("عَلِيمٌۢ"))
    }

    @Test
    fun aVerseIsTheKeysOfItsWords() {
        assertEquals(
            WordKey.ofVerse("۞ فَبِأَيِّ ءَالَآءِ رَبِّكُمَا تُكَذِّبَانِ"),
            WordKey.ofVerse("فَبِأَيِّ  ءَالَآءِ رَبِّكُمَا تُكَذِّبَانِۚ")
        )
        assertNotEquals(WordKey.ofVerse("إِنَّ هَٰذَا"), WordKey.ofVerse("أَنَّ هَٰذَا"))
        assertEquals("", WordKey.ofVerse(" ۞ "))
    }
}
