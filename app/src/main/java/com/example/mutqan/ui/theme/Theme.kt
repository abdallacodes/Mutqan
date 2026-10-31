package com.example.mutqan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.mutqan.data.Palette
import com.example.mutqan.data.ThemeKey

// ── Paper: one theme in two versions ──────────────────────────────────────────
// Accents: primary is the action colour, tertiary is the gold used for marks and ornaments.

private val paperLightScheme = lightColorScheme(
    primary              = PaperGreen,
    onPrimary            = PaperBg,
    primaryContainer     = PaperGreenContainer,
    onPrimaryContainer   = PaperOnGreenContainer,
    secondary            = PaperInkSoft,
    onSecondary          = PaperBg,
    secondaryContainer   = PaperSurfaceVariant,
    onSecondaryContainer = PaperInk,
    tertiary             = PaperGoldText,
    background           = PaperBg,
    onBackground         = PaperInk,
    surface              = PaperSurface,
    onSurface            = PaperInk,
    surfaceVariant       = PaperSurfaceVariant,
    onSurfaceVariant     = PaperInkSoft,
    outline              = PaperRule,
    outlineVariant       = PaperRuleSoft,
    error                = PaperError,
    surfaceTint          = PaperSurface,
    surfaceBright        = PaperSurface,
    surfaceDim           = PaperSurfaceVariant,
    surfaceContainerLowest  = Color(0xFFF9F4E6),
    surfaceContainerLow     = Color(0xFFF6F0E0),
    surfaceContainer        = Color(0xFFF1E9D5),
    surfaceContainerHigh    = Color(0xFFEDE4CC),
    surfaceContainerHighest = Color(0xFFE8DEC3),
)

private val paperDarkScheme = darkColorScheme(
    primary              = NightGold,
    onPrimary            = NightBg,
    primaryContainer     = NightGoldContainer,
    onPrimaryContainer   = NightOnGoldContainer,
    secondary            = NightInkSoft,
    onSecondary          = NightBg,
    secondaryContainer   = NightSurfaceVariant,
    onSecondaryContainer = NightInk,
    tertiary             = NightGold,
    background           = NightBg,
    onBackground         = NightInk,
    surface              = NightSurface,
    onSurface            = NightInk,
    surfaceVariant       = NightSurfaceVariant,
    onSurfaceVariant     = NightInkSoft,
    outline              = NightRule,
    outlineVariant       = NightRuleSoft,
    error                = NightError,
    surfaceTint          = NightSurface,
    surfaceBright        = NightSurfaceVariant,
    surfaceDim           = NightBg,
    surfaceContainerLowest  = Color(0xFF100D0A),
    surfaceContainerLow     = Color(0xFF1B1712),
    surfaceContainer        = Color(0xFF201B15),
    surfaceContainerHigh    = Color(0xFF28221A),
    surfaceContainerHighest = Color(0xFF302920),
)

// Small radii: printed, not bubbly.
private val paperShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(14.dp)
)

@Composable
fun MutqanTheme(
    themeKey: ThemeKey = ThemeKey.AUTO,
    palette: Palette = Palette.PAPER,
    content: @Composable () -> Unit
) {
    val dark = when (themeKey) {
        ThemeKey.AUTO  -> isSystemInDarkTheme()
        ThemeKey.LIGHT -> false
        ThemeKey.DARK  -> true
    }
    val arabic = LocalConfiguration.current.locales[0].language == "ar"

    MaterialTheme(
        colorScheme = paletteColors(palette, dark)?.toScheme(dark) ?: if (dark) paperDarkScheme else paperLightScheme,
        typography  = paperTypography(arabic),
        shapes      = paperShapes,
        content     = content
    )
}
