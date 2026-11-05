package com.example.mutqan.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.example.mutqan.data.Palette

// ── Design system v2 ("Illuminated folio"): the colours, shapes and spacing that sit beside the Material scheme ──
// Plan and reasons: AI_KB/qmemo_kb/ui/design-upgrade-plan.md. Read them with [FolioTheme].

/**
 * Surfaces and accents the Material scheme has no name for.
 * [folio] is the raised card; [hero] the one deep-colour card of a screen, with [onHero] text and [heroAccent] for its frame,
 * pattern and button; [tonal] and [onTonal] are icon medallions, secondary buttons and pills; [edge] outlines a folio;
 * [shadow] tints the shadows. In dark versions the card is a lighter tone instead of a shadow.
 */
@Immutable
class FolioColors(
    val folio: Color,
    val edge: Color,
    val hero: Color,
    val onHero: Color,
    val heroAccent: Color,
    val tonal: Color,
    val onTonal: Color,
    val shadow: Color,
    val dark: Boolean
)

private fun c(hex: Long) = Color(0xFF000000 or hex)

private val Parchment = c(0xFBF3E6)
private val LightGold = c(0xDDB872)

/**
 * The tokens of [palette] in a light or dark version. Derived from the palette's own colours; Paper and Plum have
 * a hand-tuned hero (and Plum a hand-tuned folio) because their derived values were not right on a phone.
 */
fun folioColors(palette: Palette, dark: Boolean): FolioColors {
    val p = paletteColors(palette, dark) ?: paperColors(dark)
    if (dark) {
        return FolioColors(
            folio = lerp(p.surface, p.surfaceVariant, 0.5f),
            edge = p.ruleSoft,
            hero = p.primaryContainer,
            onHero = p.onPrimaryContainer,
            heroAccent = p.tertiary,
            tonal = p.primaryContainer,
            onTonal = p.onPrimaryContainer,
            shadow = Color.Black,
            dark = true
        )
    }
    val hero = when (palette) {
        Palette.PAPER -> PaperOnGreenContainer
        Palette.PLUM -> c(0x45173C)
        else -> lerp(p.primary, Color.Black, 0.42f)
    }
    return FolioColors(
        folio = if (palette == Palette.PLUM) c(0xFFFBFD) else lerp(p.surface, Color.White, 0.6f),
        edge = p.ruleSoft,
        hero = hero,
        onHero = Parchment,
        heroAccent = LightGold,
        tonal = p.primaryContainer,
        onTonal = p.onPrimaryContainer,
        shadow = hero,
        dark = false
    )
}

/** Corner radii: controls 14, cards 20, the hero card 22, pills fully round. */
@Immutable
class FolioShapes {
    val control = RoundedCornerShape(14.dp)
    val card = RoundedCornerShape(20.dp)
    val hero = RoundedCornerShape(22.dp)
    val medallion = RoundedCornerShape(12.dp)
    val pill = RoundedCornerShape(percent = 50)
}

/** Spacing steps (4 · 8 · 12 · 16 · 20 · 24 · 32) and the gutter between the screen edge and content. */
@Immutable
class FolioSpace {
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s20 = 20.dp
    val s24 = 24.dp
    val s32 = 32.dp
    val gutter = 20.dp
}

val LocalFolioColors = staticCompositionLocalOf { folioColors(Palette.PAPER, dark = false) }

object FolioTheme {
    val colors: FolioColors
        @Composable @ReadOnlyComposable get() = LocalFolioColors.current
    val shapes = FolioShapes()
    val space = FolioSpace()
}
