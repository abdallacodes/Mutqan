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
        assertEquals(4, first.dials.sky)
        assertTrue(first.dials.helpersAtOnce)
        val late = LanternsLevels.rules(120)
        assertEquals(LanternsLevels.MOST_WORDS, late.words)
        assertTrue(late.dials.speed < 0.26f)
        assertTrue(late.dials.interval > 0.55f)
        assertEquals(7, late.dials.sky)
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

    @Test
    fun aLookAlikeComesRightAfterItsVerse() {
        val pool = List(20) { VerseLength(it + 1, 5) }
        val partners = mapOf(1 to listOf(11), 11 to listOf(1), 2 to listOf(12), 12 to listOf(2))
        repeat(20) { seed ->
            val ids = LevelVerses.pick(pool, 12, 2, emptyList(), emptyList(), Random(seed), setOf(1, 2, 11, 12), 1f) { partners[it].orEmpty() }
            assertEquals(4, ids.size)
            for (i in ids.indices step 2) assertEquals("$ids", partners.getValue(ids[i]).single(), ids[i + 1])
        }
    }

    @Test
    fun theOpenRoundPlaysALookAlikeRightAfterItsVerse() {
        val pool = List(10) { VerseLength(it + 1, 5) }
        val partners = mapOf(3 to listOf(7, 9), 7 to listOf(3))
        repeat(10) { seed ->
            val ids = LevelVerses.order(pool, emptyList(), emptyList(), Random(seed), 8, partners = { partners[it].orEmpty() })
            assertEquals(10, ids.toSet().size)
            assertEquals("$ids", 1, kotlin.math.abs(ids.indexOf(3) - ids.indexOf(7)))
        }
    }

    @Test
    fun versesWithLookAlikesComeFirstByTheirChance() {
        val pool = List(40) { VerseLength(it + 1, 5) }
        val favoured = (1..40 step 4).toSet()
        repeat(10) { seed ->
            val ids = LevelVerses.pick(pool, 12, 2, emptyList(), emptyList(), Random(seed), favoured, 1f)
            assertEquals(4, ids.size)
            assertTrue("$ids", ids.all { it in favoured })
        }
        // Without the chance, a level draws them as any other.
        val plain = (0 until 50).sumOf { seed -> LevelVerses.pick(pool, 12, 2, emptyList(), emptyList(), Random(seed)).count { it in favoured } }
        assertTrue("$plain of 200", plain in 20..80)
        // The open round too.
        val open = LevelVerses.order(pool, emptyList(), emptyList(), Random(1), 8, favoured, 1f)
        assertEquals(favoured, open.take(favoured.size).toSet())
    }

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
        val letters = "بتثجحخدذرزسشصضطظعغفقكلمنهوي"
        val names = "abcdefgkqxzmnphijlorstuvwy"
        return verses.map { verse -> verse.split(' ').map { w -> w.map { letters[names.indexOf(it)] }.joinToString("") } }
    }

    private fun places(found: List<NextWords.Candidate>) = found.map { it.place }

    @Test
    fun findsTheWordThatFollowsTheSameTwoWordsElsewhere() {
        val next = NextWords.build(words("a b c d", "x a b e", "a b c f", "q a b g"))
        val found = next.candidates(1)
        // Word 2 of ayah 1 is "c": after "a b", ayah 2 has "e" and ayah 4 "g"; ayah 3 has "c" too, so it is no candidate.
        assertEquals(setOf(NextWords.Place(2, 3), NextWords.Place(4, 3)), places(found[2]).toSet())
        assertEquals(1, next.candidates(1, max = 1)[2].size)
        // Word 3 ("d"), after "a b c" (both verses start so): ayah 3 goes on with "f".
        assertEquals(listOf(NextWords.Place(3, 3)), places(found[3]))
        // Word 1 ("b"): no verse starting with "a" has another word next.
        assertTrue(found[1].isEmpty())
    }

    @Test
    fun theMoreWordsAgreeOnBothSidesTheCloser() {
        // Word 3 of ayah 1 ("c", before "d"): ayah 2 has "x" with "d" after it too, ayah 3 "y" with another word after.
        val next = NextWords.build(words("p a b c d", "q a b x d", "r a b y z"))
        assertEquals(listOf(NextWords.Place(2, 3), NextWords.Place(3, 3)), places(next.candidates(1)[3]))
    }

    @Test
    fun aWordPutInBeforeTheSameWordsIsACandidate() {
        // وَمَا ٱلۡحَيَوٰةُ ٱلدُّنۡيَآ إِلَّا ... and وَمَا هَٰذِهِ ٱلۡحَيَوٰةُ ٱلدُّنۡيَآ إِلَّا ...: after "w", "t" may come before "h d a".
        val next = NextWords.build(words("w h d a l", "w t h d a k"))
        assertEquals(NextWords.Place(2, 1), next.candidates(1)[1].first().place)
    }

    @Test
    fun aLookAlikeVerseGivesItsWordsWhereAFewDiffer() {
        // The same three words, then each its own: the look-alike's words stand in each place, the swap in the middle too.
        val next = NextWords.build(words("a b c l w f", "a b c h k g", "m n o p"))
        val plain = next.candidates(1)
        assertEquals(listOf(NextWords.Place(2, 3)), places(plain[3]))
        assertTrue("not known to look alike", plain[4].isEmpty())
        val known = next.candidates(1, alike = mapOf(2 to NextWords.FROM_LIST))
        assertEquals(listOf(NextWords.Place(2, 4)), places(known[4]))
        assertEquals(listOf(NextWords.Place(2, 5)), places(known[5]))
    }

    @Test
    fun aVerseFoundSharingMuchIsLaidBesideToo() {
        // Four words agree (after "b": "c d e" in both), so the other verse is laid beside without being known.
        val next = NextWords.build(words("a b c d e x y", "q b c d e u v"))
        assertEquals(listOf(NextWords.Place(2, 6)), places(next.candidates(1)[6]))
    }

    @Test
    fun theOpeningOfALookAlikeVerseIsACandidateForTheFirstWord() {
        // ذَٰلِكَ ٱلۡكِتَٰبُ لَا رَيۡبَۛ فِيهِۛ and تَنزِيلُ ٱلۡكِتَٰبِ لَا رَيۡبَ فِيهِ.
        val next = NextWords.build(words("d k l r f h", "t k l r f m"))
        val known = next.candidates(1, alike = mapOf(2 to NextWords.FROM_LIST))
        assertEquals(listOf(NextWords.Place(2, 0)), places(known[0]))
    }

    @Test
    fun aMatchByChanceIsLeftOutUnlessTheReaderKnowsTheVerse() {
        // Only the first word agrees: too weak, unless the other verse is one the reader knows.
        val next = NextWords.build(words("q a c d e", "q b f g h"))
        assertTrue(next.candidates(1)[1].isEmpty())
        assertEquals(listOf(NextWords.Place(2, 1)), places(next.candidates(1, near = { it == 2 })[1]))
    }

    @Test
    fun aVerseTheReaderKnowsComesFirst() {
        val next = NextWords.build(words("p a b c d e", "q a b x d v", "r a b y u v"))
        assertEquals(NextWords.Place(2, 3), next.candidates(1)[3].first().place)
        assertEquals(NextWords.Place(3, 3), next.candidates(1, near = { it == 3 })[3].first().place)
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
        // After the same two words ayah 1 has أَنَّ: ayah 2 has إِنَّ and ayah 5 أَنُّ, both another word; ayahs 3 and 4
        // have the same word (a stop sign is no part of it).
        assertEquals(setOf(NextWords.Place(2, 2), NextWords.Place(5, 2)), places(next.candidates(1)[2]).toSet())
    }

    @Test
    fun versesSharingFourWordsAreLookAlikes() {
        val next = NextWords.build(words("a b c d e", "x b c d e", "q r s t", "y b c z w", "a b c d e"))
        // Ayah 2 shares four words, ayah 5 all of them; ayah 4 only two.
        assertEquals(setOf(2, 5), next.lookAlikes(1).toSet())
        assertEquals(0.8f, next.likeness(1, 2), 0.001f)
        assertEquals(1f, next.likeness(1, 5), 0.001f)
        assertEquals(0f, next.likeness(1, 3), 0.001f)
    }

    @Test
    fun aLongVerseHoldingAShortOneIsLessAlikeThanOneOfItsLength() {
        val next = NextWords.build(words("a b c d", "x y z a b c d u v w o", "q b c d"))
        assertTrue(next.likeness(1, 3) > next.likeness(1, 2))
    }

    @Test
    fun aVerseOwnWordsAreThoseNoneOfTheOthersHas() {
        val next = NextWords.build(words("a b c d e", "a b x d e", "q b c d e"))
        // Against ayah 2 alone: "c" is its own; against both: only "a"/"c" shared by one or the other, nothing own.
        assertEquals(listOf(3..3), next.ownWords(1, listOf(2)))
        assertEquals(emptyList<IntRange>(), next.ownWords(1, listOf(2, 3)))
        assertEquals(listOf(1..1, 3..3), next.ownWords(3, listOf(2)))
    }

    @Test
    fun theWordsTwoVersesShareAreFoundWordForWord() {
        val next = NextWords.build(words("a b c d", "x a b y d"))
        assertEquals(listOf(2..3, 5..5), next.sharedWords(1, 2))
    }

    @Test
    fun aPlaceIsAWordIdWhenTheIdsAreKnown() {
        val next = NextWords.build(words("a b", "c d e"), intArrayOf(10, 13))
        assertEquals(15, next.wordId(NextWords.Place(2, 2)))
        assertEquals(null, NextWords.build(words("a b")).wordId(NextWords.Place(1, 1)))
    }
}

class TrapsMixTest {

    private val verse = GameVerse(1, List(4) { GameWord(it + 1, "w$it") }, given = 1)

    private fun source(traps: Traps, seed: Int) = LanternsSource(
        listOf(verse), emptyMap(), verse.words, emptyMap(),
        traps = mapOf(1 to mapOf(2 to traps)), trapChance = 1f, once = true, random = Random(seed)
    )

    @Test
    fun aTrapIsMostOftenAWordThatCouldComeNextTheCloserTheLikelier() {
        val traps = Traps(listOf(GameWord(10, "close"), GameWord(11, "far")), listOf(5f, 2f), listOf(GameWord(20, "near")))
        val first = HashMap<String, Int>()
        val source = source(traps, 7)
        repeat(2000) { source.decoys(verse, 2, 4).first().key.let { first[it] = (first[it] ?: 0) + 1 } }
        val follows = (first["close"] ?: 0) + (first["far"] ?: 0)
        assertTrue("follows $follows", follows in 1350..1650)
        assertTrue("near ${first["near"]}", (first["near"] ?: 0) in 350..650)
        assertTrue("the closer first: $first", (first["close"] ?: 0) > 3 * (first["far"] ?: 0))
    }

    @Test
    fun withOnlyOneKindThatKindComes() {
        val near = source(Traps(emptyList(), emptyList(), listOf(GameWord(20, "near"))), 3)
        repeat(50) { assertEquals("near", near.decoys(verse, 2, 4).first().key) }
        val follows = source(Traps(listOf(GameWord(10, "next")), listOf(2f), emptyList()), 3)
        repeat(50) { assertEquals("next", follows.decoys(verse, 2, 4).first().key) }
    }

    @Test
    fun theOtherKindStandsBehindIncaseTheFirstIsUp() {
        val traps = Traps(listOf(GameWord(10, "next")), listOf(3f), listOf(GameWord(20, "near")))
        val source = source(traps, 5)
        repeat(50) { assertEquals(setOf("next", "near"), source.decoys(verse, 2, 4).take(2).map { it.key }.toSet()) }
    }
}

class WordKeyTest {

    @Test
    fun aTanweenRunOnIntoBaIsThePlainTanween() {
        assertEquals(WordKey.of("أَلِيمٌ"), WordKey.of("أَلِيمُۢ"))
        assertEquals(WordKey.of("كَافِرٍ"), WordKey.of("كَافِرِۭ"))
        assertEquals(WordKey.of("أَبَدًا"), WordKey.of("أَبَدَۢا"))
        assertEquals(WordKey.of("أَبَدٗا"), WordKey.of("أَبَدَۢا"))
        assertEquals(WordKey.of("مِنۡ"), WordKey.of("مِنۢ"))
        // A plain damma is no tanween, and a meem inside a word stays as it is.
        assertNotEquals(WordKey.of("أَلِيمُ"), WordKey.of("أَلِيمٌ"))
        assertEquals(WordKey.of("أَنبِـُٔونِي"), WordKey.of("أَنۢبِـُٔونِي"))
    }

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
    fun marksTheNextWordPutsOnAWordAreNoPartOfIt() {
        // A shadda on the first letter from the word before merging into it.
        assertEquals(WordKey.of("رَبِّكَ"), WordKey.of("رَّبِّكَ"))
        // A sukun on the last letter, written or not by what follows.
        assertEquals(WordKey.of("هُمۡ"), WordKey.of("هُم"))
        assertEquals(WordKey.of("مِنۡ"), WordKey.of("مِّن"))
        // A madda on the last letter before a hamza; a silent alef after it does not count as the last letter.
        assertEquals(WordKey.of("إِذَا"), WordKey.of("إِذَآ"))
        assertEquals(WordKey.of("ءَامَنُواْ"), WordKey.of("ءَامَنُوٓاْ"))
        // But a vowel is a vowel, and a madda inside the word belongs to it.
        assertNotEquals(WordKey.of("مِن"), WordKey.of("مَن"))
        assertNotEquals(WordKey.of("عَلَيۡهِمۡ"), WordKey.of("عَلَيۡهِمُ"))
        assertNotEquals(WordKey.of("ٱلسَّمَآءِ"), WordKey.of("ٱلسَّمَاءِ"))
        assertNotEquals(WordKey.of("رَبِّكَ"), WordKey.of("رَبَّكَ"))
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

class WordTrapsTest {

    private val words = listOf(
        "إِنَّ", "أَنَّ", "وَإِن", "إِن", "مِن", "مَن", "يَعۡلَمُونَ", "تَعۡلَمُونَ", "رَبِّهِمۡ", "رَبِّكُمۡ",
        "فَمَن", "وَمَن", "ٱلۡكِتَٰبَ", "كِتَٰبٗا", "مُؤۡمِنُونَ", "مُؤۡمِنِينَ", "بِٱللَّهِ", "لِلَّهِ", "ٱللَّهِ", "ٱلسَّمَآءِ", "ٱلسَّمَآءُ"
    ).mapIndexed { i, w -> (i + 1) to w }
    private val traps = WordTraps.build(words)
    private fun of(word: String) = traps.traps(WordKey.of(word), 8).map { it.first }

    @Test
    fun theSameLettersWithOtherMarksComeFirst() {
        assertTrue(WordKey.of("أَنَّ") in of("إِنَّ").take(3))
        assertTrue(WordKey.of("مَن") in of("مِن"))
        assertTrue(WordKey.of("ٱلسَّمَآءُ") in of("ٱلسَّمَآءِ"))
    }

    @Test
    fun anotherPersonPronounJoiningLetterOrEnding() {
        assertTrue(WordKey.of("تَعۡلَمُونَ") in of("يَعۡلَمُونَ"))
        assertTrue(WordKey.of("رَبِّكُمۡ") in of("رَبِّهِمۡ"))
        assertTrue(WordKey.of("وَمَن") in of("فَمَن"))
        assertTrue(WordKey.of("مُؤۡمِنِينَ") in of("مُؤۡمِنُونَ"))
        assertTrue(WordKey.of("لِلَّهِ") in of("بِٱللَّهِ") || WordKey.of("ٱللَّهِ") in of("بِٱللَّهِ"))
    }

    @Test
    fun neverTheWordItselfAndOnlyRealWords() {
        val all = words.map { WordKey.of(it.second) }.toSet()
        for ((_, w) in words) {
            val found = of(w)
            assertTrue(WordKey.of(w) !in found)
            assertTrue(found.all { it in all })
        }
    }
}

class AlikeFocusTest {

    @Test
    fun aVerseCountsWhenALookAlikeIsKnownOrInTheReadersGroups() {
        val alike = AlikeVerses(mates = mapOf(1 to listOf(9))) { id -> if (id == 2) listOf(3) else if (id == 4) listOf(8) else emptyList() }
        val close = { id: Int -> if (id == 5) listOf(6) else emptyList() }
        val known = { id: Int -> id == 3 || id == 6 }
        val focus = AlikeFocus.build(listOf(1, 2, 3, 4, 5, 6, 7), alike, close, known)
        // 1 has a group mate (counted even on a page not known), 2 a known ready-list look-alike, 5 a known close one;
        // 4's look-alike is on a page not known, and the rest have none.
        assertEquals(setOf(1, 2, 5), focus.favoured)
        assertEquals(listOf(3), focus.partnersOf(2))
        assertEquals(emptyList<Int>(), focus.partnersOf(1))
        assertEquals(listOf(6), focus.partnersOf(5))
    }
}
