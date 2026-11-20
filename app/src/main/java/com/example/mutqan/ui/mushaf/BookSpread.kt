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
