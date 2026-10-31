package com.example.mutqan.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.mutqan.data.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PalettesTest {

    /** WCAG contrast ratio between two colours. */
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun check(name: String, ratio: Double, min: Double) =
        assertTrue("$name has contrast $ratio, below $min", ratio >= min)

    @Test
    fun `every palette except Paper has a light and a dark version`() {
        for (palette in Palette.entries) {
            if (palette == Palette.PAPER) {
                assertNull(paletteColors(palette, false))
            } else {
                assertNotNull(paletteColors(palette, false))
                assertNotNull(paletteColors(palette, true))
            }
        }
    }

    @Test
    fun `text is readable on its background in every palette and version`() {
        for (palette in Palette.entries.filter { it != Palette.PAPER }) {
            for (dark in listOf(false, true)) {
                val p = paletteColors(palette, dark)!!
                val name = "$palette ${if (dark) "dark" else "light"}"
                check("$name ink on background", contrast(p.ink, p.bg), 7.0)
                check("$name ink on surface", contrast(p.ink, p.surface), 7.0)
                check("$name soft ink on background", contrast(p.inkSoft, p.bg), 4.5)
                check("$name soft ink on surface variant", contrast(p.inkSoft, p.surfaceVariant), 4.0)
                check("$name primary on background", contrast(p.primary, p.bg), 4.5)
                check("$name text on the action colour", contrast(p.onPrimary, p.primary), 4.5)
                check("$name text on the action container", contrast(p.onPrimaryContainer, p.primaryContainer), 4.5)
                check("$name second accent on background", contrast(p.tertiary, p.bg), 3.0)
                check("$name error on background", contrast(p.error, p.bg), 3.0)
            }
        }
    }

    @Test
    fun `the swatch of Paper is its own colours`() {
        assertEquals(PaperBg, paletteSwatch(Palette.PAPER, false).first)
        assertEquals(NightBg, paletteSwatch(Palette.PAPER, true).first)
    }

    @Test
    fun `palette names are read back, and unknown ones fall back to Paper`() {
        assertEquals(Palette.PLUM, Palette.fromString("PLUM"))
        assertEquals(Palette.PAPER, Palette.fromString("nonsense"))
        assertEquals(Palette.PAPER, Palette.fromString(null))
    }
}
