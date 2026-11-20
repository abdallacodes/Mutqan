package com.example.mutqan.ui.mushaf

import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sqrt

/*
 * A page turned by hand goes at the pace of paper. However hard it is flicked, a page cannot turn in a blink: the air
 * holds it, and it comes down gently. A pager left to itself snaps to the next page in about a fifth of a second after
 * a flick, and then the leaf, the view following it and the book seen smaller all happen too fast to follow. So the rest
 * of a turn after the finger lets go is drawn at the pace of paper: an even pace that a quick finger does not hurry (it
 * never starts faster than its average), coming to rest softly. The book seen smaller during a turn grows back with the
 * page, and fastest of all just before the page lies flat, so the page has to slow down to nothing over its last part: a
 * first version slowed down evenly to a stop, and the book seemed to rush back to its size at the end.
 */

/** The motion of the rest of a turn, in pure numbers, so it can be checked without a screen. */
internal object PaperPace {

    /** How long a whole page takes to turn. */
    const val FULL_MILLIS = 1000L
    /** The least time even a small rest of a turn takes, so it never flashes. */
    const val MIN_MILLIS = 350L
    /**
     * How fast, as a share of its average speed, the motion may start when the finger was moving: a slower finger is
     * carried on at its own speed, a quicker one is not followed (a first version followed up to twice the average, and
     * a page let go of halfway still went by too fast to see).
     */
    const val MAX_START = 1f

    /** How long the rest of a turn [distance] long takes, a page being [page] long: a whole page [FULL_MILLIS], less for less. */
    fun durationMillis(distance: Float, page: Float): Long {
        if (distance == 0f || page <= 0f) return 0L
        val part = (abs(distance) / page).coerceIn(0f, 1f)
        return max(MIN_MILLIS, (FULL_MILLIS * sqrt(part)).roundToLong())
    }

    /**
     * How fast the motion starts, as a share of its average speed: the finger's speed [velocity] (units a second) carried
     * on toward the page it is going to, but never more than [MAX_START]; nothing if the finger was going the other way.
     */
    fun startSlope(distance: Float, velocity: Float, seconds: Float): Float {
        if (distance == 0f || seconds <= 0f) return 0f
        return (velocity * seconds / distance).coerceIn(0f, MAX_START)
    }

    /**
     * How far along [distance] the motion is at [u] (0..1 of its time), starting at [slope]. It comes to rest softly: at
     * the end both its speed and how fast its speed changes are nothing (a quartic: x(0) = 0, x'(0) = slope, x(1) = 1,
     * x'(1) = x''(1) = 0). Its speed is (1 − u)² · ((12 − 4·slope)·u + slope), never below nothing for a slope up to 3,
     * so it never goes back or past the page.
     */
    fun position(u: Float, distance: Float, slope: Float): Float {
        val t = u.coerceIn(0f, 1f)
        return distance * t * (slope + t * ((6f - 3f * slope) + t * ((3f * slope - 8f) + t * (3f - slope))))
    }

    /** The speed at [u], in units a second, for a motion lasting [seconds]. */
    fun velocity(u: Float, distance: Float, slope: Float, seconds: Float): Float {
        if (seconds <= 0f) return 0f
        val t = u.coerceIn(0f, 1f)
        val left = 1f - t
        return distance / seconds * left * left * ((12f - 4f * slope) * t + slope)
    }
}

/**
 * The pager's settling at the pace of paper ([PaperPace]); [pageSize] is the length of a page in pixels, read when a
 * motion starts.
 */
internal class PaperSettle(private val pageSize: () -> Int) : FiniteAnimationSpec<Float> {

    override fun <V : AnimationVector> vectorize(converter: TwoWayConverter<Float, V>): VectorizedFiniteAnimationSpec<V> =
        object : VectorizedFiniteAnimationSpec<V> {

            private fun millis(initialValue: V, targetValue: V): Long =
                PaperPace.durationMillis(converter.convertFromVector(targetValue) - converter.convertFromVector(initialValue), pageSize().toFloat())

            override fun getDurationNanos(initialValue: V, targetValue: V, initialVelocity: V): Long =
                millis(initialValue, targetValue) * 1_000_000L

            override fun getValueFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V {
                val from = converter.convertFromVector(initialValue)
                val distance = converter.convertFromVector(targetValue) - from
                val millis = millis(initialValue, targetValue)
                if (millis == 0L) return targetValue
                val seconds = millis / 1000f
                val slope = PaperPace.startSlope(distance, converter.convertFromVector(initialVelocity), seconds)
                val u = playTimeNanos / (millis * 1_000_000f)
                return converter.convertToVector(from + PaperPace.position(u, distance, slope))
            }

            override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V {
                val distance = converter.convertFromVector(targetValue) - converter.convertFromVector(initialValue)
                val millis = millis(initialValue, targetValue)
                if (millis == 0L) return converter.convertToVector(0f)
                val seconds = millis / 1000f
                val slope = PaperPace.startSlope(distance, converter.convertFromVector(initialVelocity), seconds)
                val u = playTimeNanos / (millis * 1_000_000f)
                return converter.convertToVector(PaperPace.velocity(u, distance, slope, seconds))
            }
        }
}
