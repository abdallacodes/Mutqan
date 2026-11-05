package com.example.mutqan.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.mutqan.ui.theme.FolioTheme

private const val FillMs = 700

/**
 * A ring that fills clockwise from the top to [fraction], with [content] in the middle, usually the number it stands
 * for. The fill animates when [fraction] changes. [description] is what TalkBack reads along with the percentage.
 */
@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    stroke: Dp = 8.dp,
    track: Color = FolioTheme.colors.tonal,
    color: Color = MaterialTheme.colorScheme.primary,
    description: String? = null,
    content: @Composable () -> Unit = {}
) {
    val target = fraction.coerceIn(0f, 1f)
    val shown by animateFloatAsState(target, tween(FillMs, easing = FastOutSlowInEasing), label = "ring")
    val side = size.withFontScale(1.3f)
    Box(
        modifier = modifier
            .size(side)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
                if (description != null) contentDescription = description
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(side)) {
            val w = stroke.toPx()
            val arc = Size(this.size.width - w, this.size.height - w)
            val topLeft = Offset(w / 2f, w / 2f)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(w))
            if (shown > 0f) {
                drawArc(color, -90f, 360f * shown, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/**
 * A thick rounded bar that fills to [fraction], optionally cut into [segments] cells (juz, say) with a small gap
 * between them. Replaces the 3dp line; the fill animates when [fraction] changes.
 */
@Composable
fun PageStrip(
    fraction: Float,
    modifier: Modifier = Modifier,
    segments: Int = 1,
    height: Dp = 10.dp,
    track: Color = FolioTheme.colors.tonal,
    color: Color = MaterialTheme.colorScheme.primary,
    description: String? = null
) {
    val target = fraction.coerceIn(0f, 1f)
    val shown by animateFloatAsState(target, tween(FillMs, easing = FastOutSlowInEasing), label = "strip")
    val cells = segments.coerceAtLeast(1)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
                if (description != null) contentDescription = description
            }
    ) {
        val gap = if (cells > 1) 2.dp.toPx() else 0f
        val cellWidth = (size.width - gap * (cells - 1)) / cells
        val radius = CornerRadius(size.height / 2f)
        // The bar starts at the side reading starts from: the right in Arabic.
        val rtl = layoutDirection == LayoutDirection.Rtl
        for (i in 0 until cells) {
            val left = if (rtl) size.width - (i + 1) * cellWidth - i * gap else i * (cellWidth + gap)
            drawRoundRect(track, Offset(left, 0f), Size(cellWidth, size.height), radius)
            // How much of this cell is filled: the part of [shown] that falls inside it.
            val inCell = ((shown * cells) - i).coerceIn(0f, 1f)
            if (inCell > 0f) {
                val filled = maxOf(cellWidth * inCell, size.height)
                drawRoundRect(color, Offset(if (rtl) left + cellWidth - filled else left, 0f), Size(filled, size.height), radius)
            }
        }
    }
}
