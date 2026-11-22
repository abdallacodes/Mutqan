package com.example.mutqan.domain.game

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Plays every level many times with a simulated player and checks that each level is passed less often than the one
 * before it (beyond what chance in so many plays allows), and clearly less often than five levels before.
 *
 * The player looks for a word once it is the one wanted (having seen its lantern before does not help): it finds it
 * after a moment that grows with the lanterns in the sky (and with the wind), and taps it if it is still there. Now and
 * then it mixes a word up: more often when a look-alike word is in the sky, and at the start of a verse begun from
 * nothing. It catches the helpers it sees.
 */
class LevelCurveTest {

    private class Player {
        val react = 0.5f
        val readEach = 0.38f
        val slip = 0.012f
        val alikeSlip = 0.07f
        val startSlip = 0.06f
    }

    /** A level's verses as the app would choose them from pages of verses of every length, with made-up words. */
    private fun source(rules: LevelRules, random: Random): Pair<LanternsSource, Set<String>> {
        val lengths = List(120) { LanternsSource.MIN_WORDS + random.nextInt(rules.longestVerse - LanternsSource.MIN_WORDS + 1) }
        val pool = lengths.mapIndexed { i, words -> VerseLength(1 + i, words) }
        val ids = LevelVerses.pick(pool, rules.words, rules.given, emptyList(), random)
        var next = 1
        val verses = ids.map { id -> GameVerse(id, List(lengths[id - 1]) { w -> GameWord(next++, "v${id}w$w") }, rules.given) }
        val alike = HashSet<String>()
        // About a third of the words have a look-alike next word elsewhere, as in the Quran.
        val lookAlikes = verses.associate { verse ->
            verse.ayahId to verse.words.indices.filter { random.nextFloat() < 0.3f }.associateWith { at ->
                listOf(GameWord(next++, "alike${verse.ayahId}_$at").also { alike += it.key })
            }
        }
        val neighbours = verses.associate { it.ayahId to List(6) { w -> GameWord(next++, "n${it.ayahId}_$w") } }
        val source = LanternsSource(
            verses, neighbours, verses.flatMap { it.words }, emptyMap(),
            lookAlikes = lookAlikes, lookAlikeChance = rules.lookAlike, once = true, random = random
        )
        return source to alike
    }

    /** One play of [level]: true when it was won. */
    private fun play(level: Int, seed: Int): Boolean {
        val rules = LanternsLevels.rules(level)
        val random = Random(seed)
        val (source, alike) = source(rules, Random(seed * 31 + 7))
        val game = LanternsGame(source, Random(seed * 17 + 3), rules.dials)
        game.laneXs = listOf(0.16f, 0.39f, 0.61f, 0.84f)
        game.lanternHeight = 0.17f
        game.escapeY = 0.24f
        game.start()
        val player = Player()
        val seenAt = HashMap<Long, Float>()
        var busyUntil = 0f
        var wantedKey: String? = null
        var wantedSince = 0f
        var t = 0f
        val dt = 0.05f
        while (!game.over && t < 900f) {
            game.update(dt)
            t += dt
            game.events.clear()
            val visible = game.lanterns.filter { it.state == LanternState.RISING && it.y < 0.98f }
            for (lantern in visible) seenAt.getOrPut(lantern.id) { t }
            if (game.needed?.key != wantedKey) {
                wantedKey = game.needed?.key
                wantedSince = t
            }
            if (t < busyUntil || game.celebrating > 0f) continue
            val words = visible.count { it.kind == LanternKind.WORD }
            val look = (player.react + player.readEach * words) * (1f + rules.dials.wind * 6f)
            val helper = visible.firstOrNull { it.kind != LanternKind.WORD && t - seenAt.getValue(it.id) >= look }
            if (helper != null) {
                game.tap(helper.id)
                busyUntil = t + 0.3f
                continue
            }
            val wanted = visible.firstOrNull { it.kind == LanternKind.WORD && it.word?.key == game.needed?.key } ?: continue
            if (t - maxOf(wantedSince, seenAt.getValue(wanted.id)) < look) continue
            var chance = player.slip
            if (visible.any { it.word?.key in alike }) chance += player.alikeSlip
            if (game.verse?.given == 0 && game.placed == 0) chance += player.startSlip
            val wrong = visible.firstOrNull { it.kind == LanternKind.WORD && it.word?.key != game.needed?.key }
            game.tap(if (wrong != null && random.nextFloat() < chance) wrong.id else wanted.id)
            busyUntil = t + 0.3f
        }
        return game.cleared
    }

    @Test
    fun eachLevelIsPassedLessOftenThanTheOneBefore() {
        val levels = 1..40
        // Plays of different seeds for each level, so one level's luck is not shared with the next.
        val passed = levels.map { level -> (1..PLAYS).count { play(level, level * 100_000 + it) } / PLAYS.toFloat() }
        println(levels.zip(passed).joinToString("\n") { (level, rate) -> "level $level: ${"%.3f".format(rate)}" })
        for (i in 1 until passed.size) {
            assertTrue("level ${i + 1} (${passed[i]}) passed more often than level $i (${passed[i - 1]})", passed[i] <= passed[i - 1] + CHANCE)
        }
        for (i in 5 until passed.size) {
            assertTrue("level ${i + 1} (${passed[i]}) not harder than level ${i - 4} (${passed[i - 5]})", passed[i] < passed[i - 5] || passed[i - 5] == 1f)
        }
        assertTrue("the first level should be easy: ${passed.first()}", passed.first() > 0.95f)
        assertTrue("level 40 should still be possible: ${passed.last()}", passed.last() > 0.1f)
    }

    private companion object {
        const val PLAYS = 1000

        /** What chance alone can add to a pass rate measured over [PLAYS] plays (over two standard errors). */
        const val CHANCE = 0.05f
    }
}
