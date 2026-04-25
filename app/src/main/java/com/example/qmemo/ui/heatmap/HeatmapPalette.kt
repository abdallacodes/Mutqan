package com.example.qmemo.ui.heatmap

import androidx.compose.ui.graphics.Color
import com.example.qmemo.domain.PageStability
import kotlin.math.pow

internal fun pageColor(ps: PageStability): Color {
    if (!ps.isTracked) return Color(0xFF1E2020)
    return healthColor(ps.score)
}

/** Packed ARGB for lists / arrays — same pixels as [pageColor], no extra [Color] retention. */
internal fun pageColorValue(ps: PageStability): ULong =
    if (!ps.isTracked) 0xFF1E2020UL
    else healthColor(ps.score).value

internal fun healthColor(score: Float): Color {
    if (!score.isFinite() || score <= 0f) return Color(0xFF1E2020)
    val hue = score.coerceIn(0f, 1f) * 120f
    return Color.hsv(hue, saturation = 0.82f, value = 0.76f)
}

internal fun adaptiveTextColor(bgColor: Color): Color {
    fun lin(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    val lum = 0.2126 * lin(bgColor.red.toDouble()) +
        0.7152 * lin(bgColor.green.toDouble()) +
        0.0722 * lin(bgColor.blue.toDouble())
    return if (lum > 0.179) Color(0xFF0D0D0D) else Color.White
}
