package com.example.mutqan.ui.mushaf

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/*
 * The two-page view as a book: the whole open book in sight, both pages side by side, always; which is what the one-page
 * view shows halfway through a turn. It is the one-page pager with pages half its width, coming to rest only on a spread
 * (a right-hand page, with the left-hand one beside it), so a turn there is the same leaf turning, seen without drawing
 * back and without the view moving (see Modifier.leafTurn).
 */

/** Two pages side by side: each page half the pager's width. */
internal object HalfPage : PageSize {
    override fun Density.calculateMainAxisPageSize(availableSpace: Int, pageSpacing: Int): Int = (availableSpace - pageSpacing) / 2
}

/** Where the two-page book comes to rest: on a spread, an even pager page. */
internal object SpreadSnap {

    /**
     * The pager page a turn let go at [at] (current page plus offset) settles on, going at [velocity] (pixels a second,
     * toward later pages when positive): a spread, the one before [at] or the one after it. A fling faster than [fast]
     * goes its way; a slower let-go settles on the nearer of the two. Never past the last spread before page [last].
     */
    fun target(at: Float, velocity: Float, fast: Float, last: Int): Int {
        val from = 2 * floor(at / 2f).toInt()
        val into = (at - from) / 2f
        val to = when {
            velocity > fast -> from + 2
            velocity < -fast -> from
            into < 0.5f -> from
            else -> from + 2
        }
        return to.coerceIn(0, last - last % 2)
    }
}

/** The fling of the two-page book: it settles on a spread ([SpreadSnap]), at the pace of [snap]. */
@Composable
internal fun rememberSpreadFling(state: PagerState, snap: AnimationSpec<Float>): TargetedFlingBehavior {
    val decay = rememberSplineBasedDecay<Float>()
    val fast = with(LocalDensity.current) { FLING_FAST.toPx() }
    return remember(state, snap, decay, fast) {
        snapFlingBehavior(
            snapLayoutInfoProvider = object : SnapLayoutInfoProvider {
                // A turn is one leaf: no running on past it, straight to the spread.
                override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float = 0f

                override fun calculateSnapOffset(velocity: Float): Float {
                    val page = state.layoutInfo.pageSize + state.layoutInfo.pageSpacing
                    if (page <= 0) return 0f
                    val at = state.currentPage + state.currentPageOffsetFraction
                    return (SpreadSnap.target(at, velocity, fast, state.pageCount - 1) - at) * page
                }
            },
            decayAnimationSpec = decay,
            snapAnimationSpec = snap
        )
    }
}

/** A finger let go faster than this sends the leaf the way it was going; slower, it settles on the nearer spread. */
private val FLING_FAST = 400.dp

/**
 * A turn of the two-page book held by a finger. The book is narrower than the screen held sideways (desk on both sides),
 * and the pager moves a page for every half screen the finger goes; so with the pager's own drag a whole turn took the
 * whole width of the screen while the leaf's edge crossed only the book, and the edge, which barely moves while the paper
 * lifts from the binding, fell behind the finger and caught up late (2026-11-20, user: "it doesn't stick to my finger, it
 * moves late"). Here the point of the leaf the finger took stays under the finger: the turn is set to where that point,
 * as drawn (bent and seen in perspective, see LeafCurve), is under it.
 */
internal object SpreadDrag {
    private const val STEPS = 240

    /**
     * Where the point [u] of the way out from the binding (0) to the free edge (1) of a leaf turning forward is seen, at
     * [progress] of the turn: the binding at [spine] (the middle of the screen, under the eye), the page [length] wide,
     * the eye [eye] above the book.
     */
    fun heldX(progress: Float, u: Float, spine: Float, length: Float, eye: Float): Float {
        val points = LeafCurve.section(progress, spine, length)
        val f = u.coerceIn(0f, 1f) * LeafCurve.STRIPS
        val i = floor(f).toInt().coerceAtMost(LeafCurve.STRIPS - 1)
        val k = f - i
        val x = points[i].x + (points[i + 1].x - points[i].x) * k
        val z = points[i].y + (points[i + 1].y - points[i].y) * k
        return LeafCurve.project(x, 0f, z, spine, 0f, eye).x
    }

    /** How far a forward turn has gone when the point [u] of the leaf is seen at [x]: the first such progress, 0 to 1. */
    fun progressAt(x: Float, u: Float, spine: Float, length: Float, eye: Float): Float {
        var before = heldX(0f, u, spine, length, eye)
        if (x <= before) return 0f
        for (n in 1..STEPS) {
            val now = heldX(n.toFloat() / STEPS, u, spine, length, eye)
            if (now >= x) return (n - 1 + (x - before) / max(now - before, 1e-3f)) / STEPS
            before = now
        }
        return 1f
    }

    /**
     * How much further the turn goes for each pixel the finger goes, at [progress]: for a flick's speed in turns. Kept
     * within reason near the ends, where the held point hardly moves.
     */
    fun slope(progress: Float, u: Float, spine: Float, length: Float, eye: Float): Float {
        val d = 1f / 60f
        val a = (progress - d).coerceIn(0f, 1f - d)
        val dx = heldX(a + d, u, spine, length, eye) - heldX(a, u, spine, length, eye)
        return (d / max(dx, 1e-3f)).coerceAtMost(3f / length)
    }
}

/**
 * The finger's drag in the two-page book (see [SpreadDrag]); the pager's own drag is off there. A drag to the right turns
 * the left-hand page forward, to the left the right-hand page back, and letting go goes on with [fling] as the pager's own
 * drag did. [hand] hears when a drag starts (the landing tick is for turns made by hand); [enabled] is false while zoomed in.
 */
internal fun Modifier.spreadTurnDrag(
    state: PagerState,
    fling: TargetedFlingBehavior,
    hand: MutableInteractionSource,
    enabled: () -> Boolean
): Modifier = pointerInput(state, fling) {
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!enabled()) return@awaitEachGesture
            val taken = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                ?: return@awaitEachGesture
            val w = (size.width / 2).toFloat()
            val pageSize = state.layoutInfo.pageSize.toFloat() + state.layoutInfo.pageSpacing
            if (w <= 0f || pageSize <= 0f) return@awaitEachGesture
            val sheet = BookFrame.sheet(w, size.height.toFloat(), this@pointerInput)
            val length = sheet.width
            val eye = LeafCurve.EYE * w
            val at = state.currentPage + state.currentPageOffsetFraction
            val from = 2 * round(at / 2f).toInt()
            val last = state.pageCount - 1
            val forward = taken.position.x > down.position.x
            if ((forward && from + 2 > last - last % 2) || (!forward && from <= 0)) return@awaitEachGesture
            // A turn back is a turn forward seen in a mirror about the binding.
            fun seen(x: Float) = if (forward) x else 2f * w - x
            val start = seen(down.position.x)
            // Held where it was taken on the page that turns; in its inner half as if by its middle (the paper there stays
            // down while the leaf lifts from its edge, so it would not follow); off the page, by its edge. Either way
            // the held point follows the finger from where the finger came down.
            val u = ((w - start) / length).let { when { it in 0.5f..1f -> it; it in 0f..0.5f -> 0.5f; else -> 1f } }
            val held = w - u * length
            fun progress(x: Float) = SpreadDrag.progressAt(held + seen(x) - start, u, w, length, eye)
            fun place(t: Float) = if (forward) from + 2f * t else from - 2f * t

            val targets = Channel<Float>(Channel.UNLIMITED)
            var velocity = 0f
            val grab = DragInteraction.Start()
            hand.tryEmit(grab)
            launch {
                state.scroll(MutatePriority.UserInput) {
                    var now = state.currentPage + state.currentPageOffsetFraction
                    for (target in targets) now += scrollBy((target - now) * pageSize) / pageSize
                    with(fling) { performFling(velocity) }
                }
            }
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var t = progress(taken.position.x)
            targets.trySend(place(t))
            horizontalDrag(taken.id) { change ->
                tracker.addPosition(change.uptimeMillis, change.position)
                t = progress(change.position.x)
                targets.trySend(place(t))
                change.consume()
            }
            // Either way the turn goes on at the finger's speed: the pager moves 2 pages a turn.
            velocity = 2f * SpreadDrag.slope(t, u, w, length, eye) * tracker.calculateVelocity().x * pageSize
            targets.close()
            hand.tryEmit(DragInteraction.Stop(grab))
        }
    }
}
