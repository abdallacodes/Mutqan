package com.example.mutqan.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.mutqan.data.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the design system v2 tokens: every palette (Paper included) in both versions keeps its pairs readable. */
class FolioColorsTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun check(name: String, a: Color, b: Color, min: Double) {
        val ratio = contrast(a, b)
        assertTrue("$name has contrast $ratio, below $min", ratio >= min)
    }

    private fun forEverySchemeIncludingPaper(block: (name: String, p: PaletteColors, f: FolioColors) -> Unit) {
        for (palette in Palette.entries) {
            for (dark in listOf(false, true)) {
                val p = paletteColors(palette, dark) ?: paperColors(dark)
                block("$palette ${if (dark) "dark" else "light"}", p, folioColors(palette, dark))
            }
        }
    }

    @Test
    fun `text and marks are readable on the folio card`() = forEverySchemeIncludingPaper { name, p, f ->
        check("$name ink on folio", p.ink, f.folio, 7.0)
        check("$name soft ink on folio", p.inkSoft, f.folio, 4.5)
        check("$name action colour on folio", p.primary, f.folio, 4.5)
        check("$name second accent on folio", p.tertiary, f.folio, 3.0)
        check("$name error on folio", p.error, f.folio, 3.0)
    }

    @Test
    fun `the hero card keeps its text, accent and button readable`() = forEverySchemeIncludingPaper { name, _, f ->
        check("$name text on hero", f.onHero, f.hero, 7.0)
        check("$name accent on hero", f.heroAccent, f.hero, 4.5)
        check("$name hero colour on the accent (hero button label)", f.hero, f.heroAccent, 4.5)
    }

    @Test
    fun `icons and labels are readable on the tonal fill`() = forEverySchemeIncludingPaper { name, p, f ->
        check("$name text on tonal", f.onTonal, f.tonal, 4.5)
        check("$name tonal fill against the folio it sits on", f.tonal, f.folio, 1.05)
        check("$name text on the action colour (primary button)", p.onPrimary, p.primary, 4.5)
    }

    @Test
    fun `the hero card stands apart from the page`() = forEverySchemeIncludingPaper { name, p, f ->
        // In the light versions the hero is the deepest colour on the screen; in the dark ones it is a raised tone.
        check("$name hero against the page", f.hero, p.bg, if (f.dark) 1.2 else 5.0)
    }

    @Test
    fun `the folio card is a different tone from the page`() = forEverySchemeIncludingPaper { name, p, f ->
        assertNotEquals("$name folio equals the page background", p.bg, f.folio)
    }

    @Test
    fun `tokens follow the palette in use`() {
        assertEquals(PaperOnGreenContainer, folioColors(Palette.PAPER, false).hero)
        assertEquals(paletteColors(Palette.LAPIS, true)!!.primaryContainer, folioColors(Palette.LAPIS, true).hero)
        assertTrue(folioColors(Palette.GRAPHITE, true).dark)
    }
}
