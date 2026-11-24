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
    val golden: Float = 0f,
    /**
     * How many words after the wanted one are kept in the sky too, so the next is already up when it is wanted (a word
     * tapped before its turn is a slip): 0 is the wanted word alone.
     */
    val ahead: Int = 0,
    /** How much faster the whole sky moves for each right word in a row (up to [LanternsGame.FLOW_RUN]); 0 keeps it steady. */
    val flow: Float = 0f
)

/** What a level brings that the one before it did not: shown on its card on the map. */
enum class LevelNews {
    START,
    ONE_GIVEN,
    FIFTH_LANTERN,
    LOOK_ALIKE,
    GOLDEN,
    SIXTH_LANTERN,
    WIND,
    NONE_GIVEN,
    SEVENTH_LANTERN,

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
    /**
     * The chance that a verse with look-alikes (the reader's mutashabihat groups, the ready lists) is put before the
     * others when the level's verses are drawn: from [LanternsLevels.LOOK_ALIKE_FROM] on, the verses readers mix up
     * come more often.
     */
    val lookAlike: Float,
    /** The chance that a wrong lantern is a trap (a word easy to take for the one wanted), where the word has some. */
    val traps: Float,
    /** Seconds the wanted word shows after a slip. */
    val hintTime: Float,
    val dials: Dials
)

/**
 * The levels of «فوانيس», each harder than the one before it.
 *
 * Small dials turn a notch at every level: the lanterns rise faster and come more often, the helpers come more rarely,
 * the hint after a slip shows for less time, and there is a word more to build (up to [MOST_WORDS]); more of the wrong
 * lanterns are traps; later the wind blows harder and verses with look-alikes come more often. They grow more slowly as
 * the levels go up but never stop, so a late level is still a little harder than the one before it and still playable.
 * A few levels bring one big step each, never taken back: a word fewer given (levels 5 and 25), a lantern more in the
 * sky (8, 18, 30), verses with look-alikes (10), wind (20). Golden lanterns (15) only add points. A level is a number of
 * words to build, not of verses, so drawing short verses cannot make it easier.
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
            speed = toward(START_SPEED, END_SPEED, 0.955f, k),
            interval = toward(START_INTERVAL, END_INTERVAL, 0.955f, k),
            // The wanted word, the next, and two wrong ones at least: the right words are never most of the sky.
            sky = 4 + MORE_LANTERNS_AT.count { n >= it },
            helperEvery = toward(14f, 40f, 0.95f, k),
            helpersAtOnce = true,
            wind = if (n < WIND_FROM) 0f else toward(0.015f, 0.05f, 0.93f, n - WIND_FROM),
            golden = if (n < GOLDEN_FROM) 0f else GOLDEN_SHARE,
            ahead = 1,
            // A run quickens the sky, up to a top pace the late levels reach on their own (so it matters most early on,
            // where the levels were too slow for a reader who knows the verses).
            flow = FLOW
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
            traps = toward(FIRST_TRAPS, MOST_TRAPS, 0.93f, k),
            hintTime = toward(2f, 0.8f, 0.95f, k),
            dials = dials
        )
    }

    private const val GOLDEN_SHARE = 0.1f

    /**
     * The share of wrong lanterns that are traps: half at level 1, heading for four in five (most of them words that
     * could really come next, which take more telling apart than any other wrong word).
     */
    private const val FIRST_TRAPS = 0.5f
    private const val MOST_TRAPS = 0.8f

    /** Level 1's pace: about what level 7 was before (the first levels were too slow for a reader who knows the verses). */
    private const val START_SPEED = 0.095f
    private const val START_INTERVAL = 1.2f

    /** Where the pace heads (never quite there): faster than before, since the next word is up and reading it can start early. */
    private const val END_SPEED = 0.26f
    private const val END_INTERVAL = 0.55f

    /** Each right word in a row makes the sky this much faster (up to [LanternsGame]'s top pace). */
    private const val FLOW = 0.04f

    fun news(level: Int): LevelNews = when (level) {
        1 -> LevelNews.START
        ONE_GIVEN_FROM -> LevelNews.ONE_GIVEN
        MORE_LANTERNS_AT[0] -> LevelNews.FIFTH_LANTERN
        LOOK_ALIKE_FROM -> LevelNews.LOOK_ALIKE
        GOLDEN_FROM -> LevelNews.GOLDEN
        MORE_LANTERNS_AT[1] -> LevelNews.SIXTH_LANTERN
        WIND_FROM -> LevelNews.WIND
        NONE_GIVEN_FROM -> LevelNews.NONE_GIVEN
        MORE_LANTERNS_AT[2] -> LevelNews.SEVENTH_LANTERN
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
     * The furthest level open: the one after the furthest won, or the two after it when that one was won with three
     * stars (a reader who finds the levels easy leaps over one). The levels before it are open too.
     */
    fun furthestOpen(stars: List<Int>): Int = if (stars.isEmpty()) 1 else stars.size + if (stars.last() >= 3) 2 else 1

    /**
     * The stars kept after winning [level] with [stars]: the better of old and new for a level played before; a level
     * further on that is open gets its stars, and a level leapt over keeps none (0) until it is won. A level beyond the
     * open ones (only reachable in a test build) is not kept.
     */
    fun withStars(kept: List<Int>, level: Int, stars: Int): List<Int> = when {
        level in 1..kept.size -> kept.toMutableList().also { it[level - 1] = maxOf(it[level - 1], stars) }
        level in (kept.size + 1)..furthestOpen(kept) -> kept + List(level - kept.size - 1) { 0 } + stars
        else -> kept
    }
}

/**
 * A verse that may go into a round: its id and how many words it has. [twin] is the same for verses with exactly the
 * same words (a refrain the Quran says again and again): they count as one verse.
 */
data class VerseLength(val ayahId: Int, val words: Int, val twin: Int = ayahId)

/**
 * Which verses a round holds. The same verse must not keep coming: verses with the same words are one verse, the verses
 * drawn lately wait while others are left, and a slipped verse comes back only after a while and never in great number.
 */
object LevelVerses {
    /** The fewest words a verse leaves to build to be worth a place in a level. */
    const val LEAST_TO_BUILD = 2

    /** A slipped verse waits until this many other verses have been drawn before it comes back (fewer where there are few). */
    const val COOL_DOWN = 12

    /** The fewest different verses a level should draw from: with fewer, the next longer verses are let in too. */
    const val MIN_POOL = 24

    /** A verse as the round sees it: [play] stands for every verse with the same words. */
    private class Verse(val play: VerseLength, val slipRank: Int?, val seenRank: Int?) {
        val words: Int get() = play.words
    }

    /**
     * The verses as three lists, each to be taken before the next: the slipped ones that are due (not among the last
     * [COOL_DOWN] drawn; the most recent slip first), the ones not drawn lately in random order (of them, each of
     * [favoured] put first by the chance [favour]), and the rest, the one drawn longest ago first. Of verses with the
     * same words the one played is the slipped one, else the one drawn longest ago (or never).
     */
    private fun tiers(
        verses: List<VerseLength>,
        slipped: List<Int>,
        recent: List<Int>,
        random: Random,
        favoured: Set<Int> = emptySet(),
        favour: Float = 0f
    ): List<List<Verse>> {
        val slipOrder = slipped.withIndex().associate { (i, id) -> id to i }
        val seenOrder = HashMap<Int, Int>()
        recent.forEachIndexed { i, id -> seenOrder.putIfAbsent(id, i) }
        val all = verses.groupBy { it.twin }.values.map { same ->
            val play = same.filter { it.ayahId in slipOrder }.minByOrNull { slipOrder.getValue(it.ayahId) }
                ?: same.shuffled(random).maxBy { seenOrder[it.ayahId] ?: Int.MAX_VALUE }
            Verse(
                play = play,
                slipRank = same.mapNotNull { slipOrder[it.ayahId] }.minOrNull(),
                seenRank = same.mapNotNull { seenOrder[it.ayahId] }.minOrNull()
            )
        }
        val wait = min(COOL_DOWN, all.size / 3)
        val due = all.filter { it.slipRank != null && (it.seenRank == null || it.seenRank >= wait) }.sortedBy { it.slipRank }
        val rest = all.filter { verse -> due.none { it === verse } }
        val fresh = rest.filter { it.seenRank == null }.shuffled(random).let { list ->
            if (favour <= 0f || favoured.isEmpty()) list
            else list.partition { it.play.ayahId in favoured && random.nextFloat() < favour }.let { (first, after) -> first + after }
        }
        val seen = rest.filter { it.seenRank != null }.sortedByDescending { it.seenRank }
        return listOf(due, fresh, seen)
    }

    /**
     * Verses for the open round in the order to play them: the slipped ones that are due first (at most [maxSlipped]),
     * then the ones not drawn lately in random order ([favoured] ones first by the chance [favour]), then the rest, the
     * one drawn longest ago first. A verse's look-alikes among them ([partners]) come right after it, a few at most.
     */
    fun order(
        candidates: List<VerseLength>,
        slipped: List<Int>,
        recent: List<Int>,
        random: Random,
        maxSlipped: Int,
        favoured: Set<Int> = emptySet(),
        favour: Float = 0f,
        partners: (Int) -> List<Int> = { emptyList() },
        onlyAfter: Set<Int> = emptySet()
    ): List<Int> {
        val unique = candidates.distinctBy { it.ayahId }
        val (due, fresh, seen) = tiers(unique, slipped, recent, random, favoured, favour)
        val all = due.take(maxSlipped) + fresh + due.drop(maxSlipped) + seen
        val byTwin = all.associateBy { it.play.twin }
        val twinOf = unique.associate { it.ayahId to it.twin }
        val placed = HashSet<Int>()
        val out = ArrayList<Int>(all.size)
        for (verse in all) {
            // A verse from beyond the pages drawn from comes only right after the verse it looks like.
            if (verse.play.ayahId in onlyAfter) continue
            if (!placed.add(verse.play.twin)) continue
            out += verse.play.ayahId
            var more = 0
            for (other in partners(verse.play.ayahId)) {
                if (more >= MAX_CHAIN - 1) break
                val twin = twinOf[other] ?: continue
                val partner = byTwin[twin] ?: continue
                if (placed.add(twin)) {
                    out += partner.play.ayahId
                    more++
                }
            }
        }
        return out
    }

    /**
     * [reach] (the verses a level allows) and, when those are fewer than [MIN_POOL] different verses, the shortest of
     * [beyond] as well until there are enough (or none is left): a small hifz of long verses would otherwise bring the
     * same few verses round after round.
     */
    fun withEnough(reach: List<VerseLength>, beyond: List<VerseLength>): List<VerseLength> {
        val have = reach.map { it.twin }.toMutableSet()
        if (have.size >= MIN_POOL) return reach
        val extra = ArrayList<VerseLength>()
        for (verse in beyond.sortedBy { it.words }) {
            if (have.size >= MIN_POOL) break
            if (have.add(verse.twin)) extra += beyond.filter { it.twin == verse.twin }
        }
        return reach + extra
    }

    /**
     * Verses for a level whose words to build (their words less [given] each) add up to [target], or as near as the
     * verses allow. Verses with the same words count as one. The ones not drawn lately come first (in random order),
     * then the one drawn longest ago ([recent] holds the ids drawn, the latest first); slipped verses that are due
     * ([slipped], the most recent slip first) come before all, but are at most a third of the words; of the others,
     * [favoured] ones (with look-alikes) come first by the chance [favour]. In a round on mutashabihat, a look-alike of
     * the verse just chosen ([partners]) comes next when one fits, a few in a row at most. A verse comes once, unless
     * there are too few to fill the level. Returned in the order to play them.
     */
    fun pick(
        candidates: List<VerseLength>,
        target: Int,
        given: Int,
        slipped: List<Int>,
        recent: List<Int>,
        random: Random,
        favoured: Set<Int> = emptySet(),
        favour: Float = 0f,
        partners: (Int) -> List<Int> = { emptyList() },
        onlyAfter: Set<Int> = emptySet()
    ): List<Int> {
        fun build(verse: Verse) = verse.words - given
        val usable = candidates.distinctBy { it.ayahId }.filter { it.words - given >= LEAST_TO_BUILD }
        if (usable.isEmpty() || target <= 0) return emptyList()
        val tiers = tiers(usable, slipped, recent, random, favoured, favour)
        val due = tiers[0]

        // The fewest words any verse here leaves to build: what a level's last verse needs at least.
        val least = usable.minOf { it.words - given }
        val twinOf = usable.associate { it.ayahId to it.twin }
        var chain = 0
        var paired = false
        val chosen = ArrayList<Verse>()
        val used = HashSet<Verse>()
        var left = target
        var slipWords = 0
        var guard = 0
        while (left > 0 && guard++ < MAX_STEPS) {
            val slipsFull = slipWords >= target / 3
            var open = tiers.mapIndexed { i, tier -> tier.filter { it !in used && !(i == 0 && slipsFull) && it.play.ayahId !in onlyAfter } }
            // Nothing else is left: the slipped ones come after all.
            if (open.all { it.isEmpty() }) open = tiers.map { tier -> tier.filter { it !in used && it.play.ayahId !in onlyAfter } }
            if (open.all { it.isEmpty() }) {
                // Too few verses to fill the level: they come again.
                if (used.isEmpty()) break
                used.clear()
                continue
            }
            // A fit: the exact words left, or a verse that leaves room for one more after it, or one a word off.
            fun fit(list: List<Verse>): Verse? = list.firstOrNull { build(it) == left }
                ?: list.firstOrNull { build(it) <= left - least }
                ?: list.firstOrNull { abs(build(it) - left) <= 1 }
            // A look-alike of the verse just chosen, when one fits; else from the first list that has a fit.
            val prev = chosen.lastOrNull()
            val partner = if (prev == null || chain >= MAX_CHAIN) null else {
                val twins = partners(prev.play.ayahId).mapNotNullTo(HashSet()) { twinOf[it] } - prev.play.twin
                val waiting = tiers.flatten().filter { it !in used && it.play.twin in twins }
                // The pair matters more than the exact length: a look-alike may run a few words over.
                if (twins.isEmpty()) null else fit(waiting) ?: waiting.firstOrNull { build(it) <= left + PAIR_SLACK }
            }
            val next = partner ?: open.firstNotNullOfOrNull { fit(it) } ?: open.flatten().minBy { abs(build(it) - left) }
            chain = if (partner != null) chain + 1 else 1
            if (partner != null) paired = true
            used += next
            chosen += next
            left -= build(next)
            if (due.any { it === next }) slipWords += build(next)
        }
        // Look-alikes stay side by side; otherwise the order is drawn anew.
        return (if (paired) chosen else chosen.shuffled(random)).map { it.play.ayahId }
    }

    /** A verse and its look-alikes in a row: this many at most. */
    const val MAX_CHAIN = 3

    /** Words a look-alike may run over what a level has left, to come after its verse. */
    const val PAIR_SLACK = 6

    private const val MAX_STEPS = 200
}
