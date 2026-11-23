package com.example.mutqan.domain.game

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A word on a lantern: [id] draws it (a Quran word id), [key] compares it ([WordKey]: letter for letter and mark for mark,
 * so أَنَّ and إِنَّ are two words, and عَلِمَ and عُلِمَ).
 */
data class GameWord(val id: Int, val key: String)

/**
 * A verse to build, its words in order; the first [given] are shown placed from the start. With none given, [cue] holds
 * the last words of the verse before it (its end marker included), shown to start from: word ids, for drawing only.
 */
data class GameVerse(val ayahId: Int, val words: List<GameWord>, val given: Int, val cue: List<Int> = emptyList())

/** Where the verses and the wrong words come from. Called from the game loop: no slow work. */
interface GameSource {
    /** The next verse to build, or null when there is none. */
    fun nextVerse(): GameVerse?

    /** Words to float beside the one wanted, word [at] of [verse]: none with its text. */
    fun decoys(verse: GameVerse, at: Int, count: Int): List<GameWord>
}

enum class LanternKind {
    /** Carries a word. */
    WORD,

    /** Slows everything down for a while. */
    SLOW,

    /** Gives back a light (a life). */
    LIFE
}

/** Rising; caught by a right tap (flies to the verse); broken by a wrong tap (falls); released (the verse is done, it drifts away). */
enum class LanternState { RISING, CAUGHT, BROKEN, RELEASED }

/** One lantern in the sky, in screen fractions: [x] across, [y] down (0 top, 1 bottom), both its middle. */
class Lantern internal constructor(
    val id: Long,
    val kind: LanternKind,
    val word: GameWord?,
    internal val lane: Int,
    private val baseX: Float,
    y: Float,
    internal val speed: Float,
    private val phase: Float,
    /** Which of the lantern colours it wears. */
    val hue: Int,
    /** A golden lantern: three times the points when its word is the one wanted. */
    val golden: Boolean = false
) {
    var y: Float = y
        internal set
    var x: Float = baseX
        internal set
    var state: LanternState = LanternState.RISING
        internal set

    /** When it was sent up, in the round's time. */
    internal var born = 0f

    /** Seconds since [state] last changed, for its animation. */
    var stateTime: Float = 0f
        internal set

    /** Sways it at [time]: a little on its own, [wider] more in the wind, and pushed [gust] with all the others. */
    internal fun sway(time: Float, wider: Float = 0f, gust: Float = 0f) {
        x = baseX + (SWAY + wider) * sin(time * SWAY_SPEED + phase) + gust
    }

    internal companion object {
        const val SWAY = 0.018f
        const val SWAY_SPEED = 1.35f
    }
}

/** What happened in the last update or tap, for the screen's sounds, sparks and words. */
sealed interface GameEvent {
    data class Right(val lantern: Lantern, val points: Int, val combo: Int) : GameEvent
    data class Wrong(val lantern: Lantern) : GameEvent
    data class Missed(val lantern: Lantern) : GameEvent
    data class VerseDone(val ayahId: Int, val bonus: Int, val perfect: Boolean) : GameEvent
    data class LevelUp(val level: Int) : GameEvent
    data class PowerUp(val lantern: Lantern) : GameEvent
    data object Over : GameEvent
}

/** A verse played in a round, and how many mistakes were made in it. */
data class PlayedVerse(val ayahId: Int, val mistakes: Int, val done: Boolean)

/** How a round is played beyond its sky: its hearts, whether a slip costs one, and whether it waits after a verse. */
data class RoundRules(
    /** Hearts at the start. */
    val lives: Int = LanternsGame.START_LIVES,
    /** A slip costs no heart and the round is never lost (it is still counted, and the word wanted shows). */
    val forgiving: Boolean = false,
    /** After a verse the round waits for [LanternsGame.proceed] (while its meaning is read) instead of a short pause. */
    val waitAfterVerse: Boolean = false
)

/**
 * «فوانيس»: lanterns rise over a night town, each carrying a word; the player taps the one with the next word of the
 * verse shown at the top and builds the verse word by word. A right word scores more the longer the run of right
 * words (up to five times), a whole verse scores a bonus (twice for one without a mistake) and raises the level, which
 * makes the lanterns rise faster and come more at a time. A wrong lantern, or the right word floating away untouched,
 * puts out one of the lights; with none left the round is over. Now and then a lantern carries an hourglass (slows
 * time) or, with lights lost, a heart (one back).
 *
 * In a level of the map ([fixed] dials) the sky stays as hard from the first verse to the last, the helpers come from
 * the start, and the round is won ([cleared]) when the source has no verse left.
 *
 * Pure logic in screen fractions; the screen sets [escapeY] (where lanterns pass under the verse panel), [laneXs] and
 * [lanternHeight] from its size, calls [update] every frame and [tap] on a touch, and reads [events].
 */
class LanternsGame(
    private val source: GameSource,
    private val random: Random = Random.Default,
    /** A level's dials, for the whole level; null for the open round, whose dials rise with each verse built. */
    private val fixed: Dials? = null,
    private val rules: RoundRules = RoundRules()
) {

    /** Hearts at the start of the round. */
    val startLives: Int = rules.lives

    val lanterns = mutableListOf<Lantern>()
    val events = ArrayDeque<GameEvent>()
    val played = mutableListOf<PlayedVerse>()

    var score = 0; private set
    var combo = 0; private set
    var bestCombo = 0; private set
    var lives = startLives; private set
    var level = 1; private set
    var versesDone = 0; private set
    var right = 0; private set
    var wrong = 0; private set

    /** Times the wanted word floated away untouched. */
    var missed = 0; private set

    /** Words built in the round. */
    var built = 0; private set

    /** True when a level was won: its last verse built with a light still on. */
    var cleared = false; private set
    var verse: GameVerse? = null; private set

    /** How many words of [verse] are in place, the given ones included. */
    var placed = 0; private set

    /** Seconds of slow time left. */
    var slowLeft = 0f; private set
    var over = false; private set

    /** Seconds a finished verse is celebrated before the next comes; lanterns do not come meanwhile. */
    var celebrating = 0f; private set

    /** A verse is done and the round waits for [proceed] (see [RoundRules.waitAfterVerse]). */
    var waiting = false; private set

    /** How much faster than its dials the sky moves now: quicker with each right word in a row, back after a slip. */
    var tempo = 1f; private set

    /** The total time played, in seconds (stops while the round is over). */
    var time = 0f; private set

    /** Where a lantern goes under the verse panel and is gone, as a fraction of the height. */
    var escapeY = 0.24f

    /** The middle of each column lanterns rise in, as fractions of the width. */
    var laneXs: List<Float> = listOf(0.16f, 0.39f, 0.61f, 0.84f)

    /** A lantern's height, as a fraction of the screen's. */
    var lanternHeight = 0.17f

    /** A lantern's width, as a fraction of the screen's: columns closer than this keep clear of each other. */
    var lanternWidth = 0.27f

    private var nextId = 1L
    private var spawnIn = 0.4f
    private var powerIn = fixed?.let { it.helperEvery * HELPER_FIRST } ?: POWER_FIRST
    private var spawnsWithoutNeeded = 0
    private var verseMistakes = 0

    /** When the word wanted now became the one wanted ([time]). */
    private var neededSince = 0f

    /** The word to tap now, or null between verses. */
    val needed: GameWord? get() = verse?.words?.getOrNull(placed)

    /** How many right words in a row multiply a word's points now (1 to 5). */
    val multiplier: Int get() = multiplierFor(combo)

    /** How hard the sky is now. */
    val dials: Dials get() = fixed ?: openDials(level)

    /** Slips so far: wrong lanterns, and wanted words that floated away. */
    val slips: Int get() = wrong + missed

    fun start() {
        nextVerse()
    }

    private fun nextVerse() {
        val next = source.nextVerse()
        if (next == null) {
            cleared = fixed != null && lives > 0
            finish()
            return
        }
        verse = next
        placed = next.given.coerceIn(0, next.words.size - 1)
        neededSince = time
        verseMistakes = 0
        spawnsWithoutNeeded = 0
        spawnIn = 0.5f
    }

    /** Moves the world on by [dt] seconds. */
    fun update(dt: Float) {
        if (over) {
            age(dt)
            return
        }
        val step = dt.coerceIn(0f, MAX_STEP)
        time += step
        if (slowLeft > 0f) slowLeft = (slowLeft - step).coerceAtLeast(0f)
        // The run quickens the whole sky together (lanterns side by side never drift into each other), and a slip
        // lets it settle back, not jump.
        val aim = min(1f + dials.flow * min(combo, FLOW_RUN), maxOf(1f, TOP_SPEED / dials.speed))
        tempo += (aim - tempo) * min(1f, step * TEMPO_EASE)
        val move = (if (slowLeft > 0f) step * SLOW_FACTOR else step) * tempo

        age(step)
        val wind = dials.wind
        val gust = if (wind > 0f) wind * sin(time * GUST_SPEED) else 0f
        for (lantern in lanterns) {
            if (lantern.state != LanternState.RISING) continue
            lantern.y -= lantern.speed * move
            lantern.sway(time, wider = wind * 0.4f, gust = gust)
        }
        escape()
        if (waiting) return

        if (celebrating > 0f) {
            celebrating -= step
            if (celebrating <= 0f) {
                celebrating = 0f
                nextVerse()
            }
            return
        }
        spawnIn -= move
        if (spawnIn <= 0f) spawn()
        powerIn -= move
        if (powerIn <= 0f) power()
    }

    /** Animations run on, and a lantern whose animation is done leaves the sky. */
    private fun age(dt: Float) {
        val iterator = lanterns.iterator()
        while (iterator.hasNext()) {
            val lantern = iterator.next()
            lantern.stateTime += dt
            val gone = when (lantern.state) {
                LanternState.RISING -> false
                LanternState.CAUGHT -> lantern.stateTime > CAUGHT_TIME
                LanternState.BROKEN -> lantern.stateTime > BROKEN_TIME
                LanternState.RELEASED -> lantern.stateTime > RELEASE_TIME
            }
            if (gone) iterator.remove()
        }
    }

    /**
     * Lanterns that rose past the panel; the word needed going with them costs a light, unless it became the one needed
     * only a moment before (a lantern already high when the word before it was caught): it comes again soon instead.
     */
    private fun escape() {
        val iterator = lanterns.iterator()
        while (iterator.hasNext()) {
            val lantern = iterator.next()
            if (lantern.state != LanternState.RISING || lantern.y > escapeY) continue
            iterator.remove()
            // An old lantern of the word drifting away is no slip while a fresher one of it is still rising.
            val another = lanterns.any { it !== lantern && it.state == LanternState.RISING && it.word?.key == lantern.word?.key }
            if (lantern.kind == LanternKind.WORD && celebrating <= 0f && lantern.word?.key == needed?.key && time - neededSince >= MISS_GRACE && !another) {
                combo = 0
                missed++
                verseMistakes++
                events += GameEvent.Missed(lantern)
                loseLight()
                if (over) return
            }
        }
    }

    private fun speedNow(): Float = dials.speed

    private fun wordLanternsRising(): List<Lantern> =
        lanterns.filter { it.state == LanternState.RISING && it.kind == LanternKind.WORD }

    /** The number of word lanterns kept in the sky now. */
    private fun target(): Int = dials.sky

    /** The word wanted now and the [Dials.ahead] words after it in the verse: all kept in the sky. */
    private fun wanted(verse: GameVerse): List<GameWord> =
        verse.words.subList(placed.coerceAtMost(verse.words.size), minOf(verse.words.size, placed + 1 + dials.ahead))

    /**
     * Of [words] (the wanted one first, then the ones after it), those with no rising lantern of their own (a word
     * wanted twice needs two). A lantern sent up early for the word now wanted that is already high by its turn does not
     * count: a fresh one comes, and the old one drifting away is no slip.
     */
    private fun notUp(words: List<GameWord>, rising: List<Lantern>): List<GameWord> {
        val up = HashMap<String, ArrayList<Lantern>>()
        for (lantern in rising) lantern.word?.let { up.getOrPut(it.key) { ArrayList() } += lantern }
        val fresh = escapeY + FRESH_ROOM
        return words.filterIndexed { i, word ->
            val lanterns = up[word.key] ?: return@filterIndexed true
            val usable = if (i == 0) lanterns.firstOrNull { it.y > fresh || it.born >= neededSince } else lanterns.firstOrNull()
            if (usable != null) lanterns.remove(usable)
            usable == null
        }
    }

    private fun spawn() {
        val verse = verse ?: return
        val needed = needed ?: return
        val rising = wordLanternsRising()
        val wanted = wanted(verse)
        val missing = notUp(wanted, rising)
        val hasNeeded = missing.firstOrNull() != needed
        val ahead = missing.filter { it != needed }
        // A sky full of wrong words still makes room for the right one: it may come as one more.
        val full = rising.size >= target()
        if (rising.size >= target() + if (hasNeeded) 0 else 1) {
            spawnIn = 0.3f
            return
        }
        val lane = freeLane() ?: run {
            spawnIn = 0.15f
            return
        }
        val word = when {
            // The right word is not always the newest lantern: it comes in its turn among the others, but soon.
            !hasNeeded && (full || spawnsWithoutNeeded >= 1 || random.nextFloat() < 0.55f) -> needed
            // The words after it come before the sky fills with wrong ones (a place is kept for each), but not always
            // straight after a tap: then the newest lantern would always be the next word.
            ahead.isNotEmpty() && (rising.size + ahead.size >= target() || random.nextFloat() < 0.45f) -> ahead.first()
            else -> {
                val shown = rising.mapNotNull { it.word?.key }.toSet()
                val keep = wanted.mapTo(HashSet()) { it.key }
                // Wrong words for the word wanted and for the ones after it, so the right word is not simply the one that
                // has been up longest.
                val at = placed + random.nextInt(wanted.size)
                source.decoys(verse, at, target() + DECOYS_SPARE).firstOrNull { it.key !in keep && it.key !in shown }
                    ?: source.decoys(verse, placed, target() + DECOYS_SPARE).firstOrNull { it.key !in keep && it.key !in shown }
                    ?: missing.firstOrNull() ?: needed
            }
        }
        if (word == needed) spawnsWithoutNeeded = 0 else if (!hasNeeded) spawnsWithoutNeeded++
        val golden = dials.golden > 0f && random.nextFloat() < dials.golden
        lanterns += newLantern(LanternKind.WORD, word, lane, golden)
        spawnIn = dials.interval * (0.85f + 0.3f * random.nextFloat())
        // A word wanted, now or next, is not up yet: it comes soon, whatever the pace.
        if (notUp(wanted, wordLanternsRising()).isNotEmpty()) spawnIn = min(spawnIn, NEEDED_SOON)
    }

    private fun power() {
        val dials = dials
        powerIn = dials.helperEvery * (0.8f + 0.4f * random.nextFloat())
        if (!dials.helpersAtOnce && level < 2) return
        val lane = freeLane() ?: return
        val kind = if (lives < startLives && random.nextFloat() < 0.5f) LanternKind.LIFE else LanternKind.SLOW
        lanterns += newLantern(kind, null, lane)
    }

    private fun newLantern(kind: LanternKind, word: GameWord?, lane: Int, golden: Boolean = false): Lantern = Lantern(
        id = nextId++,
        kind = kind,
        word = word,
        lane = lane,
        baseX = laneXs[lane] + (random.nextFloat() - 0.5f) * 0.03f,
        y = SPAWN_Y,
        // One speed for all at a level, so lanterns side by side never drift into each other.
        speed = speedNow(),
        phase = random.nextFloat() * 2f * PI.toFloat(),
        hue = random.nextInt(HUES),
        golden = golden
    ).also { it.born = time }

    /**
     * A column whose last lantern, and the last lanterns of columns too close to it to pass side by side, have risen
     * clear of where a new one starts.
     */
    private fun freeLane(): Int? {
        val clear = SPAWN_Y - lanternHeight * 1.08f
        val room = lanternWidth * 1.08f + 2f * (Lantern.SWAY + dials.wind * 0.4f)
        val free = laneXs.indices.filter { lane ->
            lanterns.none { it.state == LanternState.RISING && kotlin.math.abs(laneXs[it.lane] - laneXs[lane]) < room && it.y > clear }
        }
        return free.randomOrNull(random)
    }

    /** A touch on lantern [id]. */
    fun tap(id: Long) {
        if (over || celebrating > 0f || waiting) return
        val lantern = lanterns.firstOrNull { it.id == id && it.state == LanternState.RISING } ?: return
        when (lantern.kind) {
            LanternKind.SLOW -> {
                slowLeft = SLOW_TIME
                catch(lantern)
                events += GameEvent.PowerUp(lantern)
            }
            LanternKind.LIFE -> {
                lives = (lives + 1).coerceAtMost(maxOf(MAX_LIVES, startLives))
                catch(lantern)
                events += GameEvent.PowerUp(lantern)
            }
            LanternKind.WORD -> if (lantern.word?.key == needed?.key) rightWord(lantern) else wrongWord(lantern)
        }
    }

    private fun catch(lantern: Lantern) {
        lantern.state = LanternState.CAUGHT
        lantern.stateTime = 0f
    }

    private fun rightWord(lantern: Lantern) {
        val points = WORD_POINTS * multiplierFor(combo) * if (lantern.golden) GOLDEN_TIMES else 1
        combo++
        bestCombo = maxOf(bestCombo, combo)
        right++
        built++
        score += points
        placed++
        neededSince = time
        catch(lantern)
        val verse = verse ?: return
        if (notUp(wanted(verse), wordLanternsRising()).isNotEmpty()) spawnIn = min(spawnIn, NEEDED_SOON)
        events += GameEvent.Right(lantern, points, combo)
        if (placed >= verse.words.size) verseDone(verse)
    }

    private fun wrongWord(lantern: Lantern) {
        lantern.state = LanternState.BROKEN
        lantern.stateTime = 0f
        wrong++
        combo = 0
        verseMistakes++
        events += GameEvent.Wrong(lantern)
        loseLight()
    }

    private fun verseDone(verse: GameVerse) {
        val perfect = verseMistakes == 0
        val bonus = (VERSE_POINTS + WORD_BONUS * (verse.words.size - verse.given)) * if (perfect) 2 else 1
        score += bonus
        versesDone++
        played += PlayedVerse(verse.ayahId, verseMistakes, done = true)
        events += GameEvent.VerseDone(verse.ayahId, bonus, perfect)
        if (fixed == null && level < MAX_LEVEL) {
            level++
            events += GameEvent.LevelUp(level)
        }
        if (rules.waitAfterVerse) waiting = true else celebrating = CELEBRATION
        // The lanterns left go up and away: they are no longer part of this verse.
        release()
    }

    /** The round goes on to the next verse after waiting on one ([waiting]). */
    fun proceed() {
        if (!waiting || over) return
        waiting = false
        nextVerse()
    }

    private fun loseLight() {
        if (rules.forgiving) return
        lives--
        if (lives <= 0) {
            lives = 0
            verse?.let { if (played.none { p -> p.ayahId == it.ayahId }) played += PlayedVerse(it.ayahId, verseMistakes, done = false) }
            finish()
        }
    }

    private fun finish() {
        if (over) return
        over = true
        release()
        events += GameEvent.Over
    }

    private fun release() {
        for (lantern in lanterns) if (lantern.state == LanternState.RISING) {
            lantern.state = LanternState.RELEASED
            lantern.stateTime = 0f
        }
    }

    /** Accuracy of the round's taps on words, 0 to 1 (1 with none yet). */
    val accuracy: Float get() = if (right + wrong == 0) 1f else right.toFloat() / (right + wrong)

    companion object {
        const val START_LIVES = 3
        const val MAX_LIVES = 5
        const val MAX_LEVEL = 15
        const val HUES = 5
        const val WORD_POINTS = 100
        const val VERSE_POINTS = 300
        const val WORD_BONUS = 50
        const val CAUGHT_TIME = 0.55f
        const val BROKEN_TIME = 0.7f
        const val RELEASE_TIME = 0.8f
        const val CELEBRATION = 1.2f
        const val SLOW_TIME = 6f
        const val SLOW_FACTOR = 0.45f
        const val SPAWN_Y = 1.12f
        const val BASE_SPEED = 0.072f
        const val LEVEL_SPEED = 0.011f
        const val MAX_SPEED = 0.2f
        const val BASE_INTERVAL = 1.45f
        const val LEVEL_INTERVAL = 0.07f
        const val MIN_INTERVAL = 0.6f
        const val POWER_FIRST = 16f
        const val POWER_EVERY = 22f
        const val GOLDEN_TIMES = 3

        /** Wrong words asked for beyond the sky's size, so a full sky never runs out of them (and sends the right one twice). */
        private const val DECOYS_SPARE = 3
        private const val MAX_STEP = 0.1f

        /** How far below the panel (in screen heights) a lantern must still be to count as up for a word wanted soon. */
        private const val FRESH_ROOM = 0.3f

        /** The fastest a run may make the lanterns rise, in screen heights a second: past it no one reads in time. */
        private const val TOP_SPEED = 0.22f

        /** Right words in a row that quicken the sky; more keep the same pace. */
        const val FLOW_RUN = 10

        /** How quickly the tempo follows the run, a share a second. */
        private const val TEMPO_EASE = 2.5f

        /** At most this many seconds pass before the next lantern when the word wanted is not in the sky. */
        private const val NEEDED_SOON = 0.6f

        /** Seconds a word must have been the one wanted for its lantern floating away to count as missed. */
        const val MISS_GRACE = 1.5f

        /** In a level, the first helper comes this share of the usual wait in. */
        private const val HELPER_FIRST = 0.7f

        /** How fast the wind turns from one side to the other, in radians a second. */
        private const val GUST_SPEED = 0.45f

        /** ×1 for the first four right words in a row, then one more every four, up to ×5. */
        fun multiplierFor(combo: Int): Int = (1 + combo / 4).coerceAtMost(5)

        /** The open round's dials at its [level]: a little faster, more often and fuller with each verse built. */
        fun openDials(level: Int): Dials = Dials(
            speed = min(BASE_SPEED + LEVEL_SPEED * (level - 1), MAX_SPEED),
            interval = (BASE_INTERVAL - LEVEL_INTERVAL * (level - 1)).coerceAtLeast(MIN_INTERVAL),
            sky = min(3 + (level - 1) / 2, 6),
            helperEvery = POWER_EVERY
        )
    }
}
