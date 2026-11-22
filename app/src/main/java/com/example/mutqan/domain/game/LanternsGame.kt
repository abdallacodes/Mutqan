package com.example.mutqan.domain.game

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** A word on a lantern: [id] draws it (a Quran word id), [key] compares it (the text without marks). */
data class GameWord(val id: Int, val key: String)

/** A verse to build, its words in order; the first [given] are shown placed from the start. */
data class GameVerse(val ayahId: Int, val words: List<GameWord>, val given: Int)

/** Where the verses and the wrong words come from. Called from the game loop: no slow work. */
interface GameSource {
    /** The next verse to build, or null when there is none. */
    fun nextVerse(): GameVerse?

    /** Words to float beside the right one: none with the same text as [right]. */
    fun decoys(verse: GameVerse, right: GameWord, count: Int): List<GameWord>
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
    val hue: Int
) {
    var y: Float = y
        internal set
    var x: Float = baseX
        internal set
    var state: LanternState = LanternState.RISING
        internal set

    /** Seconds since [state] last changed, for its animation. */
    var stateTime: Float = 0f
        internal set

    internal fun sway(time: Float) {
        x = baseX + SWAY * sin(time * SWAY_SPEED + phase)
    }

    private companion object {
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

/**
 * «فوانيس»: lanterns rise over a night town, each carrying a word; the player taps the one with the next word of the
 * verse shown at the top and builds the verse word by word. A right word scores more the longer the run of right
 * words (up to five times), a whole verse scores a bonus (twice for one without a mistake) and raises the level, which
 * makes the lanterns rise faster and come more at a time. A wrong lantern, or the right word floating away untouched,
 * puts out one of the lights; with none left the round is over. Now and then a lantern carries an hourglass (slows
 * time) or, with lights lost, a heart (one back).
 *
 * Pure logic in screen fractions; the screen sets [escapeY] (where lanterns pass under the verse panel), [laneXs] and
 * [lanternHeight] from its size, calls [update] every frame and [tap] on a touch, and reads [events].
 */
class LanternsGame(private val source: GameSource, private val random: Random = Random.Default) {

    val lanterns = mutableListOf<Lantern>()
    val events = ArrayDeque<GameEvent>()
    val played = mutableListOf<PlayedVerse>()

    var score = 0; private set
    var combo = 0; private set
    var bestCombo = 0; private set
    var lives = START_LIVES; private set
    var level = 1; private set
    var versesDone = 0; private set
    var right = 0; private set
    var wrong = 0; private set
    var verse: GameVerse? = null; private set

    /** How many words of [verse] are in place, the given ones included. */
    var placed = 0; private set

    /** Seconds of slow time left. */
    var slowLeft = 0f; private set
    var over = false; private set

    /** Seconds a finished verse is celebrated before the next comes; lanterns do not come meanwhile. */
    var celebrating = 0f; private set

    /** The total time played, in seconds (stops while the round is over). */
    var time = 0f; private set

    /** Where a lantern goes under the verse panel and is gone, as a fraction of the height. */
    var escapeY = 0.24f

    /** The middle of each column lanterns rise in, as fractions of the width. */
    var laneXs: List<Float> = listOf(0.16f, 0.39f, 0.61f, 0.84f)

    /** A lantern's height, as a fraction of the screen's. */
    var lanternHeight = 0.17f

    private var nextId = 1L
    private var spawnIn = 0.4f
    private var powerIn = POWER_FIRST
    private var spawnsWithoutNeeded = 0
    private var verseMistakes = 0

    /** The word to tap now, or null between verses. */
    val needed: GameWord? get() = verse?.words?.getOrNull(placed)

    /** How many right words in a row multiply a word's points now (1 to 5). */
    val multiplier: Int get() = multiplierFor(combo)

    fun start() {
        nextVerse()
    }

    private fun nextVerse() {
        val next = source.nextVerse()
        if (next == null) {
            finish()
            return
        }
        verse = next
        placed = next.given.coerceIn(0, next.words.size - 1)
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
        val move = if (slowLeft > 0f) step * SLOW_FACTOR else step

        age(step)
        for (lantern in lanterns) {
            if (lantern.state != LanternState.RISING) continue
            lantern.y -= lantern.speed * move
            lantern.sway(time)
        }
        escape()

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

    /** Lanterns that rose past the panel; the word needed going with them costs a light. */
    private fun escape() {
        val iterator = lanterns.iterator()
        while (iterator.hasNext()) {
            val lantern = iterator.next()
            if (lantern.state != LanternState.RISING || lantern.y > escapeY) continue
            iterator.remove()
            if (lantern.kind == LanternKind.WORD && celebrating <= 0f && lantern.word?.key == needed?.key) {
                combo = 0
                verseMistakes++
                events += GameEvent.Missed(lantern)
                loseLight()
                if (over) return
            }
        }
    }

    private fun speedNow(): Float = min(BASE_SPEED + LEVEL_SPEED * (level - 1), MAX_SPEED)

    private fun wordLanternsRising(): List<Lantern> =
        lanterns.filter { it.state == LanternState.RISING && it.kind == LanternKind.WORD }

    /** The number of word lanterns kept in the sky at this level. */
    private fun target(): Int = min(3 + (level - 1) / 2, 6)

    private fun spawn() {
        val verse = verse ?: return
        val needed = needed ?: return
        val rising = wordLanternsRising()
        val hasNeeded = rising.any { it.word?.key == needed.key }
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
        // The right word is not always the newest lantern: it comes in its turn among the others, but soon.
        val word = if (!hasNeeded && (full || spawnsWithoutNeeded >= 1 || random.nextFloat() < 0.55f)) {
            spawnsWithoutNeeded = 0
            needed
        } else {
            if (!hasNeeded) spawnsWithoutNeeded++
            val shown = rising.mapNotNull { it.word?.key }.toSet()
            source.decoys(verse, needed, 4).firstOrNull { it.key != needed.key && it.key !in shown }
                ?: run { spawnsWithoutNeeded = 0; needed }
        }
        lanterns += newLantern(LanternKind.WORD, word, lane)
        spawnIn = (BASE_INTERVAL - LEVEL_INTERVAL * (level - 1)).coerceAtLeast(MIN_INTERVAL) * (0.85f + 0.3f * random.nextFloat())
    }

    private fun power() {
        powerIn = POWER_EVERY * (0.8f + 0.4f * random.nextFloat())
        if (level < 2) return
        val lane = freeLane() ?: return
        val kind = if (lives < START_LIVES && random.nextFloat() < 0.5f) LanternKind.LIFE else LanternKind.SLOW
        lanterns += newLantern(kind, null, lane)
    }

    private fun newLantern(kind: LanternKind, word: GameWord?, lane: Int): Lantern = Lantern(
        id = nextId++,
        kind = kind,
        word = word,
        lane = lane,
        baseX = laneXs[lane] + (random.nextFloat() - 0.5f) * 0.03f,
        y = SPAWN_Y,
        // One speed for all at a level, so lanterns side by side never drift into each other.
        speed = speedNow(),
        phase = random.nextFloat() * 2f * PI.toFloat(),
        hue = random.nextInt(HUES)
    )

    /** A column whose last lantern, and its neighbours' last lanterns, have risen clear of where a new one starts. */
    private fun freeLane(): Int? {
        val clear = SPAWN_Y - lanternHeight * 1.08f
        val free = laneXs.indices.filter { lane ->
            lanterns.none { it.state == LanternState.RISING && kotlin.math.abs(it.lane - lane) <= 1 && it.y > clear }
        }
        return free.randomOrNull(random)
    }

    /** A touch on lantern [id]. */
    fun tap(id: Long) {
        if (over || celebrating > 0f) return
        val lantern = lanterns.firstOrNull { it.id == id && it.state == LanternState.RISING } ?: return
        when (lantern.kind) {
            LanternKind.SLOW -> {
                slowLeft = SLOW_TIME
                catch(lantern)
                events += GameEvent.PowerUp(lantern)
            }
            LanternKind.LIFE -> {
                lives = (lives + 1).coerceAtMost(MAX_LIVES)
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
        val points = WORD_POINTS * multiplierFor(combo)
        combo++
        bestCombo = maxOf(bestCombo, combo)
        right++
        score += points
        placed++
        catch(lantern)
        events += GameEvent.Right(lantern, points, combo)
        val verse = verse ?: return
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
        if (level < MAX_LEVEL) {
            level++
            events += GameEvent.LevelUp(level)
        }
        celebrating = CELEBRATION
        // The lanterns left go up and away: they are no longer part of this verse.
        release()
    }

    private fun loseLight() {
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
        const val CELEBRATION = 1.6f
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
        private const val MAX_STEP = 0.1f

        /** ×1 for the first four right words in a row, then one more every four, up to ×5. */
        fun multiplierFor(combo: Int): Int = (1 + combo / 4).coerceAtMost(5)
    }
}
