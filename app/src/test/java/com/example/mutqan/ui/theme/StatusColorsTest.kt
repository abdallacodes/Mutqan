package com.example.mutqan.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.mutqan.data.Palette
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/** The three status colours (strong, fair, weak) must be readable on every background and look different from each other. */
class StatusColorsTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun linear(c: Float): Double = c.toDouble().let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }

    /** The colour in the Oklab space, where the distance between two points follows how different they look. */
    private fun oklab(c: Color): DoubleArray {
        val r = linear(c.red)
        val g = linear(c.green)
        val b = linear(c.blue)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        )
    }

    private fun distance(a: Color, b: Color): Double {
        val x = oklab(a)
        val y = oklab(b)
        return sqrt((0..2).sumOf { (x[it] - y[it]).pow(2) })
    }

    private fun backgrounds(dark: Boolean): List<Pair<String, Color>> {
        val sets = Palette.entries.map { it.name to (paletteColors(it, dark) ?: paperColors(dark)) }
        return sets.flatMap { (name, p) -> listOf("$name bg" to p.bg, "$name surface" to p.surface, "$name surface variant" to p.surfaceVariant) }
    }

    @Test
    fun `status colours are readable as text on every background`() {
        for (dark in listOf(false, true)) {
            val status = statusColors(onLight = !dark)
            for ((name, bg) in backgrounds(dark)) {
                for ((label, colour) in listOf("strong" to status.good, "fair" to status.fair, "weak" to status.weak)) {
                    val ratio = contrast(colour, bg)
                    assertTrue("$label on $name (${if (dark) "dark" else "light"}) has contrast $ratio", ratio >= 4.5)
                }
            }
        }
    }

    @Test
    fun `the three status colours are clearly different from each other`() {
        // Before the change, fair and weak were 0.083 apart on light backgrounds.
        for ((onLight, min) in listOf(true to 0.15, false to 0.20)) {
            val s = statusColors(onLight)
            val pairs = listOf("strong-fair" to distance(s.good, s.fair), "strong-weak" to distance(s.good, s.weak), "fair-weak" to distance(s.fair, s.weak))
            for ((name, d) in pairs) assertTrue("$name is only $d apart (${if (onLight) "light" else "dark"})", d >= min)
        }
    }
}
