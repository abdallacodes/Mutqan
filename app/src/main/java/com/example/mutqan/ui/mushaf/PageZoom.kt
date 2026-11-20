package com.example.mutqan.ui.mushaf

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.min

/*
 * Zooming into the page. There is one zoom for the whole page area, whatever it shows (one page, two pages, the open
 * book), so the two pages of the open book grow together and the book's edges with them. Two fingers zoom about the
 * point between them, which stays under them; a double tap zooms in on the point tapped, and when zoomed in, back out to
 * the whole page, gliding both ways; zoomed in, a finger moves the page and a flick carries it on. The page never leaves
 * the screen's edges, so nothing past it comes into view. Pages turn only at full size, and another page opens at full size.
 */

internal object PageZoom {
    /** The most the page is enlarged: the marks over and under the letters stand out clearly. */
    const val MAX = 4f
    /** Where a double tap takes the page: twice its size, half a line across the screen. */
    const val DOUBLE_TAP = 2f
    /** Up to this the page counts as at full size: it turns, and a double tap zooms in. */
    const val ZOOMED = 1.01f

    /**
     * The shift that keeps the zoomed area over the whole screen: none of the room past its edges shows. The zoom is about
     * the area's top-left corner, so at [scale] its left edge is at the shift's x and its right edge [scale] widths on.
     */
    fun clamp(shift: Offset, scale: Float, area: Size): Offset = Offset(
        shift.x.coerceIn(min(0f, area.width * (1f - scale)), 0f),
        shift.y.coerceIn(min(0f, area.height * (1f - scale)), 0f)
    )

    /**
     * One step of two fingers: the page zoomed by [zoom] about [focus] (a point of the screen) and moved by [pan], so the
     * point of the page under the fingers stays under them, within [1, MAX] and the screen's edges.
     */
    fun pinch(scale: Float, shift: Offset, focus: Offset, zoom: Float, pan: Offset, area: Size): Pair<Float, Offset> {
        val to = (scale * zoom).coerceIn(1f, MAX)
        val point = (focus - shift) / scale
        return to to clamp(focus + pan - point * to, to, area)
    }

    /** Where a double tap at [at] takes the page: in to [DOUBLE_TAP] about the point tapped, or back to the whole page. */
    fun doubleTap(scale: Float, shift: Offset, at: Offset, area: Size): Pair<Float, Offset> =
        if (scale > ZOOMED) 1f to Offset.Zero
        else pinch(scale, shift, at, DOUBLE_TAP / scale, Offset.Zero, area)
}

/** How far the page area is zoomed and moved (see [PageZoom]); [scope] runs its glides and flicks. */
@Stable
internal class PageZoomState(private val scope: CoroutineScope) {
    var scale by mutableFloatStateOf(1f)
        private set
    var shift by mutableStateOf(Offset.Zero)
        private set
    /** The size of the page area, in pixels. */
    var area: Size = Size.Zero

    val zoomed: Boolean get() = scale > PageZoom.ZOOMED

    private var motion: Job? = null

    /** Stops a glide or a flick, as a finger coming down on the page does. */
    fun stop() {
        motion?.cancel()
        motion = null
    }

    fun pinch(focus: Offset, zoom: Float, pan: Offset) {
        val (s, t) = PageZoom.pinch(scale, shift, focus, zoom, pan, area)
        scale = s
        shift = t
    }

    /** Moves the zoomed page by [delta]; returns what is left of it once the page is at its edge. */
    fun pan(delta: Offset): Offset {
        val to = PageZoom.clamp(shift + delta, scale, area)
        val left = delta - (to - shift)
        shift = to
        return left
    }

    fun doubleTap(at: Offset) {
        stop()
        val (toScale, toShift) = PageZoom.doubleTap(scale, shift, at, area)
        // The point tapped glides from under the finger to where it ends up, as the page grows or shrinks around it.
        val fromScale = scale
        val point = (at - shift) / fromScale
        val end = point * toScale + toShift
        motion = scope.launch {
            animate(0f, 1f, animationSpec = tween(GLIDE_MILLIS, easing = FastOutSlowInEasing)) { f, _ ->
                val s = fromScale + (toScale - fromScale) * f
                scale = s
                shift = lerp(at, end, f) - point * s
            }
        }
    }

    /** Carries the zoomed page on after a flick, slowing as it goes, until it stops or meets its edge. */
    fun fling(velocity: Offset, decay: DecayAnimationSpec<Offset>) {
        stop()
        if (!zoomed || velocity == Offset.Zero) return
        motion = scope.launch {
            AnimationState(Offset.VectorConverter, shift, velocity).animateDecay(decay) {
                shift = PageZoom.clamp(value, scale, area)
            }
        }
    }

    /** Back to the whole page at once, as when another page opens. */
    fun reset() {
        stop()
        scale = 1f
        shift = Offset.Zero
    }

    private companion object {
        const val GLIDE_MILLIS = 280
    }
}

/**
 * The zoom over the content of this box (see [PageZoom]). Two fingers and, when zoomed in, a moving finger are taken
 * before the pages see them, so the pager does not turn and a word does not open; a finger at rest or a tap is left to
 * them. [onTap] is a tap no page took (it shows or hides the bars). What a moving finger cannot take once the page is at
 * its edge goes to [overflow] (with the page filling the width, the scroll down it).
 */
internal fun Modifier.pageZoom(
    state: PageZoomState,
    onTap: () -> Unit,
    overflow: (Offset) -> Unit = {}
): Modifier = this
    .onSizeChanged { state.area = it.toSize() }
    .pointerInput(state) {
        val decay = splineBasedDecay<Offset>(this)
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            state.stop()
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var pinched = false
            var moving = false
            var travel = Offset.Zero
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                when {
                    pressed.size >= 2 -> {
                        pinched = true
                        moving = false
                        state.pinch(event.calculateCentroid(useCurrent = true), event.calculateZoom(), event.calculatePan())
                        event.changes.forEach { it.consume() }
                        tracker.resetTracking()
                    }
                    state.zoomed -> {
                        val finger = pressed.first()
                        val delta = finger.positionChange()
                        travel += delta
                        if (!moving && travel.getDistance() > viewConfiguration.touchSlop) moving = true
                        if (moving) {
                            val left = state.pan(delta)
                            if (left != Offset.Zero) overflow(left)
                            finger.consume()
                        }
                        tracker.addPosition(finger.uptimeMillis, finger.position)
                    }
                    // Zoomed back to full size with a finger still down: it does not turn the page.
                    pinched -> event.changes.forEach { it.consume() }
                }
            }
            if (moving) {
                val v = tracker.calculateVelocity()
                state.fling(Offset(v.x, v.y), decay)
            }
        }
    }
    .pointerInput(state) {
        detectTapGestures(onTap = { onTap() }, onDoubleTap = { state.doubleTap(it) })
    }
    .graphicsLayer {
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = state.scale
        scaleY = state.scale
        translationX = state.shift.x
        translationY = state.shift.y
    }
