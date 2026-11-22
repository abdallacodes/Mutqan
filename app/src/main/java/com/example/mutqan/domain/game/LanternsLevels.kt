package com.example.mutqan.domain.game

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * How hard the sky of a round is. A level of the map fixes them for the whole level; the open round raises them with
 * each verse built ([LanternsGame.openDials]).
 */
data class Dials(
    /** How fast the lanterns rise, in screen heights a second. */
    val speed: Float,
    /** Seconds from one lantern to the next, on average. */
    val interval: Float,
    /** How many word lanterns the sky holds at once. */
    val sky: Int,
    /** Seconds between two helper lanterns (an hourglass, or a heart once one is lost), on average. */
    val helperEvery: Float,
    /** Whether the helpers come from the start; the open round keeps them for its second level. */
    val helpersAtOnce: Boolean = false,
    /** How far the wind pushes all the lanterns sideways, as a fraction of the width; 0 is still air. */
    val wind: Float = 0f,
    /** The share of word lanterns that come golden (three times the points when right); 0 is none. */
    val golden: Float = 0f
)

/** What a level brings that the one before it did not: shown on its card on the map. */
enum class LevelNews {
    START,
    ONE_GIVEN,
    FOURTH_LANTERN,
    LOOK_ALIKE,
    GOLDEN,
    FIFTH_LANTERN,
    WIND,
    NONE_GIVEN,
    SIXTH_LANTERN,

    /** Only faster, with a word more to build. */
    FASTER_LONGER,

    /** Only faster. */
    FASTER
}

/** One level of «فوانيس»: the words to build in it, how its verses come, and the dials of its sky. */
data class LevelRules(
    val number: Int,
    /** Words to build in the level, over as many verses as it takes. */
    val words: Int,
    /** Words already in place when a verse comes; with none, the end of the verse before it is shown instead. */
    val given: Int,
    /** The longest verse (in words) the level may hold. */
    val longestVerse: Int,
    /** The chance that a wrong lantern carries a word from a look-alike verse, where the verse has one. */
    val lookAlike: Float,
    /** Seconds the wanted word shows after a slip. */
    val hintTime: Float,
    val dials: Dials
)

/**
 * The levels of «فوانيس», each harder than the one before it.
 *
 * Small dials turn a notch at every level: the lanterns rise faster and come more often, the helpers come more rarely,
 * the hint after a slip shows for less time, and there is a word more to build (up to [MOST_WORDS]); later the wind
 * blows harder and look-alike words come more often. They grow more slowly as the levels go up but never stop, so a
 * late level is still a little harder than the one before it and still playable. A few levels bring one big step
 * each, never taken back: a word fewer given (levels 5 and 25), a lantern more in the sky (8, 18, 30), wrong words from
 * look-alike verses (10), wind (20). Golden lanterns (15) only add points. A level is a number of words to build, not of
 * verses, so drawing short verses cannot make it easier.
 */
object LanternsLevels {
    const val FIRST_WORDS = 10
    const val MOST_WORDS = 30
    const val ONE_GIVEN_FROM = 5
    const val LOOK_ALIKE_FROM = 10
    const val GOLDEN_FROM = 15
    const val WIND_FROM = 20
    const val NONE_GIVEN_FROM = 25
    private val MORE_LANTERNS_AT = listOf(8, 18, 30)

    /** Goes from [start] (level 1) toward [end], a share [rate] of the way left at each step: never quite there. */
    private fun toward(start: Float, end: Float, rate: Float, steps: Int): Float = end - (end - start) * rate.pow(steps)

    fun rules(level: Int): LevelRules {
        val n = level.coerceAtLeast(1)
        val k = n - 1
        val dials = Dials(
            speed = toward(0.068f, 0.21f, 0.955f, k),
            interval = toward(1.5f, 0.62f, 0.955f, k),
            sky = 3 + MORE_LANTERNS_AT.count { n >= it },
            helperEvery = toward(14f, 40f, 0.95f, k),
            helpersAtOnce = true,
            wind = if (n < WIND_FROM) 0f else toward(0.015f, 0.05f, 0.93f, n - WIND_FROM),
            golden = if (n < GOLDEN_FROM) 0f else GOLDEN_SHARE
        )
        return LevelRules(
            number = n,
            words = min(FIRST_WORDS + k, MOST_WORDS),
            given = when {
                n < ONE_GIVEN_FROM -> 2
                n < NONE_GIVEN_FROM -> 1
                else -> 0
            },
            longestVerse = min(7 + k / 2, LanternsSource.MAX_WORDS),
            lookAlike = if (n < LOOK_ALIKE_FROM) 0f else toward(0.3f, 0.8f, 0.93f, n - LOOK_ALIKE_FROM),
            hintTime = toward(2f, 0.8f, 0.95f, k),
            dials = dials
        )
    }

    private const val GOLDEN_SHARE = 0.1f

    fun news(level: Int): LevelNews = when (level) {
        1 -> LevelNews.START
        ONE_GIVEN_FROM -> LevelNews.ONE_GIVEN
        MORE_LANTERNS_AT[0] -> LevelNews.FOURTH_LANTERN
        LOOK_ALIKE_FROM -> LevelNews.LOOK_ALIKE
        GOLDEN_FROM -> LevelNews.GOLDEN
        MORE_LANTERNS_AT[1] -> LevelNews.FIFTH_LANTERN
        WIND_FROM -> LevelNews.WIND
        NONE_GIVEN_FROM -> LevelNews.NONE_GIVEN
        MORE_LANTERNS_AT[2] -> LevelNews.SIXTH_LANTERN
        else -> if (rules(level).words > rules(level - 1).words) LevelNews.FASTER_LONGER else LevelNews.FASTER
    }

    /** Stars for a level built with [slips] slips (a wrong lantern, or the wanted word floating away). */
    fun stars(slips: Int): Int = when {
        slips <= 0 -> 3
        slips == 1 -> 2
        else -> 1
    }

    /** How far toward dawn the sky of [level] has come: 0 is deep night, and it nears [DAWN_MOST] but never gets there. */
    fun dawn(level: Int): Float = DAWN_MOST * (1f - 0.955f.pow((level - 1).coerceAtLeast(0)))

    private const val DAWN_MOST = 0.85f

    /** The best stars of each level won, level 1 first, as kept: one digit a level. */
    fun parseStars(text: String?): List<Int> = text.orEmpty().mapNotNull { c -> c.digitToIntOrNull()?.coerceIn(0, 3) }

    fun formatStars(stars: List<Int>): String = stars.joinToString("") { it.coerceIn(0, 3).toString() }

    /**
     * The stars kept after winning [level] with [stars]: the better of old and new for a level played before, one more
     * level for the next one. A level further on (only reachable in a test build) is not kept.
     */
    fun withStars(kept: List<Int>, level: Int, stars: Int): List<Int> = when {
        level in 1..kept.size -> kept.toMutableList().also { it[level - 1] = maxOf(it[level - 1], stars) }
        level == kept.size + 1 -> kept + stars
        else -> kept
    }
}

/** A verse that may go into a level: its id and how many words it has. */
data class VerseLength(val ayahId: Int, val words: Int)

object LevelVerses {
    /** The fewest words a verse leaves to build to be worth a place in a level. */
    const val LEAST_TO_BUILD = 2

    /**
     * Verses for a level whose words to build (their words less [given] each) add up to [target], or as near as the
     * verses allow: those in [slipped] first, most recent first and up to half the words, then the others in random
     * order. A verse comes once, unless there are too few to fill the level. Returned in the order to play them.
     */
    fun pick(candidates: List<VerseLength>, target: Int, given: Int, slipped: List<Int>, random: Random): List<Int> {
        fun build(verse: VerseLength) = verse.words - given
        val usable = candidates.distinctBy { it.ayahId }.filter { build(it) >= LEAST_TO_BUILD }
        if (usable.isEmpty() || target <= 0) return emptyList()
        val slipOrder = slipped.withIndex().associate { (i, id) -> id to i }
        val order = usable.filter { it.ayahId in slipOrder }.sortedBy { slipOrder[it.ayahId] } +
            usable.filter { it.ayahId !in slipOrder }.shuffled(random)

        // The fewest words any verse here leaves to build: what a level's last verse needs at least.
        val least = usable.minOf { build(it) }
        val chosen = ArrayList<VerseLength>()
        val used = HashSet<Int>()
        var left = target
        var slipWords = 0
        var guard = 0
        while (left > 0 && guard++ < MAX_STEPS) {
            val slipsFull = slipWords >= target / 2
            val open = order.indices.filter { it !in used && !(slipsFull && order[it].ayahId in slipOrder) }
            if (open.isEmpty()) {
                // Too few verses to fill the level: they come again.
                if (used.isEmpty()) break
                used.clear()
                continue
            }
            val pickAt = open.firstOrNull { build(order[it]) == left }
                // One that leaves room for at least one more verse after it.
                ?: open.firstOrNull { build(order[it]) <= left - least }
                ?: open.minBy { abs(build(order[it]) - left) }
            val verse = order[pickAt]
            used += pickAt
            chosen += verse
            left -= build(verse)
            if (verse.ayahId in slipOrder) slipWords += build(verse)
        }
        return chosen.shuffled(random).map { it.ayahId }
    }

    private const val MAX_STEPS = 200
}
