package com.example.mutqan.quran

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The app's loading mark: the eight-pointed star that opens a hizb quarter in the Mushaf, drawn in two thin
 * outlined squares that turn slowly while the line breathes. It stays invisible for [delayMillis], so work that
 * finishes at once (a page already on the device) never flashes it.
 */
@Composable
fun PaperLoading(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    delayMillis: Int = 250
) {
    val transition = rememberInfiniteTransition(label = "paper-loading")
    // A square looks the same every quarter turn, so turning by 90 degrees repeats without a seam.
    val turn by transition.animateFloat(
        initialValue = 0f,
        targetValue = 90f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing)),
        label = "turn"
    )
    val breath by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        appear.animateTo(1f, tween(300))
    }

    Canvas(modifier = modifier.size(size).alpha(appear.value)) {
        val half = this.size.minDimension * 0.34f
        val line = color.copy(alpha = breath)
        val stroke = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Miter)
        val topLeft = Offset(center.x - half, center.y - half)
        val square = Size(half * 2, half * 2)
        rotate(turn) { drawRect(line, topLeft, square, style = stroke) }
        rotate(turn + 45f) { drawRect(line, topLeft, square, style = stroke) }
        drawCircle(line, radius = 2.dp.toPx(), center = center)
    }
}
