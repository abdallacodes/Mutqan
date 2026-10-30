package com.example.mutqan.ui.heatmap

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.mutqan.domain.PageStability
import kotlin.math.pow

internal fun pageColor(ps: PageStability): Color {
    return pageColor(ps.score, ps.isTracked)
}

internal fun pageColor(score: Float, isTracked: Boolean): Color {
    if (!isTracked) return HealthUntracked
    return healthColor(score)
}

/** Packed ARGB for lists / arrays — optimized for primitive score input. */
internal fun pageColorValue(score: Float, isTracked: Boolean): ULong =
    if (!isTracked) HealthUntracked.value
    else healthColor(score).value

internal fun pageColorValue(ps: PageStability): ULong =
    pageColorValue(ps.score, ps.isTracked)

private val HealthLow = Color(0xFFC0614E)
private val HealthMid = Color(0xFFC9A24B)
private val HealthHigh = Color(0xFF6FA283)
private val HealthUntracked = Color(0xFF2A251D)

internal fun healthColor(score: Float): Color {
    if (!score.isFinite() || score <= 0f) return HealthUntracked
    // terracotta (forgotten) -> ochre (fading) -> sage (solid)
    val s = score.coerceIn(0f, 1f)
    return if (s < 0.5f) lerp(HealthLow, HealthMid, s / 0.5f) else lerp(HealthMid, HealthHigh, (s - 0.5f) / 0.5f)
}

internal fun adaptiveTextColor(bgColor: Color): Color {
    fun lin(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    val lum = 0.2126 * lin(bgColor.red.toDouble()) +
        0.7152 * lin(bgColor.green.toDouble()) +
        0.0722 * lin(bgColor.blue.toDouble())
    return if (lum > 0.179) Color(0xFF0D0D0D) else Color.White
}
