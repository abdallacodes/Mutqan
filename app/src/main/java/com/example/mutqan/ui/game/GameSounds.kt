package com.example.mutqan.ui.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.example.mutqan.R
import kotlin.math.pow

/**
 * The sounds of «فوانيس» (made by `scripts/make_game_sounds.py`: chimes, a muted crack, shimmers and dings, no music),
 * each with how loud it plays next to the others.
 */
internal enum class GameSound(@RawRes val res: Int, val volume: Float) {
    RIGHT(R.raw.game_right, 0.55f),
    WRONG(R.raw.game_wrong, 0.75f),
    MISSED(R.raw.game_missed, 0.65f),
    VERSE(R.raw.game_verse, 0.8f),
    PERFECT(R.raw.game_perfect, 0.7f),
    COMBO(R.raw.game_combo, 0.6f),
    SLOW(R.raw.game_slow, 0.75f),
    LIFE(R.raw.game_life, 0.75f),
    GOLDEN(R.raw.game_golden, 0.8f),
    TICK(R.raw.game_tick, 0.6f),
    GO(R.raw.game_go, 0.7f),
    STAR(R.raw.game_star, 0.85f),
    CLEAR(R.raw.game_clear, 0.85f),
    FAIL(R.raw.game_fail, 0.7f),
    TAP(R.raw.game_tap, 0.4f)
}

/**
 * Plays the game's sounds through one [SoundPool]: short sounds, quick to start, several at once. All are loaded when
 * it is made; one asked for before it has loaded is skipped. With [on] false it stays quiet.
 */
internal class GameSounds(context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val ids = IntArray(GameSound.entries.size)
    private val ready = BooleanArray(GameSound.entries.size)

    var on = true

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            val i = ids.indexOf(sampleId)
            if (status == 0 && i >= 0) ready[i] = true
        }
        for (sound in GameSound.entries) ids[sound.ordinal] = pool.load(context, sound.res, 1)
    }

    /** Plays [sound] at [pitch] (1 as made, 0.5 to 2) and [volume] (a share of its own). */
    fun play(sound: GameSound, pitch: Float = 1f, volume: Float = 1f) {
        if (!on || !ready[sound.ordinal]) return
        val v = (sound.volume * volume).coerceIn(0f, 1f)
        pool.play(ids[sound.ordinal], v, v, 1, 0, pitch.coerceIn(0.5f, 2f))
    }

    fun release() = pool.release()

    companion object {
        private const val MAX_STREAMS = 8

        /** Steps up a pentatonic scale, in semitones: the right-word chime climbs as a run grows. */
        private val RUN_STEPS = intArrayOf(0, 2, 4, 7, 9, 12)

        /** The chime's pitch for the [combo]-th right word in a row. */
        fun runPitch(combo: Int): Float = 2f.pow(RUN_STEPS[(combo - 1).coerceIn(0, RUN_STEPS.size - 1)] / 12f)

        /** The stars landing at the end of a level ring higher one after another: a major chord. */
        fun starPitch(index: Int): Float = when (index) {
            0 -> 1f
            1 -> 2f.pow(4 / 12f)
            else -> 2f.pow(7 / 12f)
        }
    }
}

/** The game's sounds for the buttons and lanterns inside it; none outside the game. */
internal val LocalGameSounds = staticCompositionLocalOf<GameSounds?> { null }

/** The game's sounds while it is open, quiet unless [on]; released when it closes. */
@Composable
internal fun rememberGameSounds(on: Boolean): GameSounds {
    val context = LocalContext.current
    val sounds = remember { GameSounds(context.applicationContext) }
    sounds.on = on
    DisposableEffect(sounds) { onDispose { sounds.release() } }
    return sounds
}
