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
import kotlin.random.Random

/**
 * The sounds of «فوانيس», sounds of nature made by `scripts/make_game_sounds.py` (no musical tones, no music: the owner
 * asked for none), each with how loud it plays next to the others.
 */
internal enum class GameSound(@RawRes val res: Int, val volume: Float) {
    /** A water drop. */
    RIGHT(R.raw.game_right, 0.7f),

    /** A twig snapping. */
    WRONG(R.raw.game_wrong, 0.85f),

    /** A candle blown out. */
    MISSED(R.raw.game_missed, 0.8f),

    /** Birds' wings taking off. */
    VERSE(R.raw.game_verse, 0.85f),

    /** Drops dripping. */
    PERFECT(R.raw.game_perfect, 0.6f),

    /** A gust of wind. */
    COMBO(R.raw.game_combo, 0.6f),

    /** Sand trickling. */
    SLOW(R.raw.game_slow, 0.9f),

    /** A heartbeat. */
    LIFE(R.raw.game_life, 0.95f),

    /** A flame flaring up. */
    GOLDEN(R.raw.game_golden, 0.8f),

    /** A knock on wood. */
    TICK(R.raw.game_tick, 0.7f),

    /** A splash. */
    GO(R.raw.game_go, 0.75f),

    /** A stone into water. */
    STAR(R.raw.game_star, 0.9f),

    /** A shower of rain. */
    CLEAR(R.raw.game_clear, 0.85f),

    /** The wind dying down. */
    FAIL(R.raw.game_fail, 0.75f),

    /** A pebble's tap. */
    TAP(R.raw.game_tap, 0.5f)
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

        /**
         * A pitch a little off the sound as made, at random: no two drops or stones in nature sound the same. Random, not
         * stepped, so nothing climbs like a tune.
         */
        fun natural(spread: Float = 0.12f): Float = 1f + (Random.nextFloat() - 0.5f) * spread
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
