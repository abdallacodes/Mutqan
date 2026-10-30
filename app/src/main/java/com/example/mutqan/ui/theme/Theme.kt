package com.example.mutqan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
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
    content: @Composable () -> Unit
) {
    val dark = when (themeKey) {
        ThemeKey.AUTO  -> isSystemInDarkTheme()
        ThemeKey.LIGHT -> false
        ThemeKey.DARK  -> true
    }
    val arabic = LocalConfiguration.current.locales[0].language == "ar"

    MaterialTheme(
        colorScheme = if (dark) paperDarkScheme else paperLightScheme,
        typography  = paperTypography(arabic),
        shapes      = paperShapes,
        content     = content
    )
}
