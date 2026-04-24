package com.example.qmemo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography as M3Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.qmemo.data.ThemeKey

// ── Four named color schemes ──────────────────────────────────────────────────

private val modernDarkScheme = darkColorScheme(
    primary              = ModernDarkPrimary,
    onPrimary            = ModernDarkOnPrimary,
    primaryContainer     = ModernDarkPrimaryContainer,
    onPrimaryContainer   = ModernDarkOnPrimaryContainer,
    tertiary             = ModernDarkTertiary,
    background           = ModernDarkBg,
    onBackground         = ModernDarkOnBg,
    surface              = ModernDarkSurface,
    onSurface            = ModernDarkOnBg,
    surfaceVariant       = ModernDarkSurfaceVariant,
    onSurfaceVariant     = ModernDarkOnSurfaceVariant,
    outline              = ModernDarkOutline,
    outlineVariant       = ModernDarkOutlineVariant,
    error                = DifficultyCritical,
)

private val oledBlackScheme = darkColorScheme(
    primary              = OLEDPrimary,
    onPrimary            = OLEDOnPrimary,
    primaryContainer     = OLEDPrimaryContainer,
    onPrimaryContainer   = OLEDOnPrimaryContainer,
    tertiary             = OLEDTertiary,
    background           = OLEDBg,
    onBackground         = OLEDOnBg,
    surface              = OLEDSurface,
    onSurface            = OLEDOnBg,
    surfaceVariant       = OLEDSurfaceVariant,
    onSurfaceVariant     = OLEDOnSurfaceVariant,
    outline              = OLEDOutline,
    outlineVariant       = OLEDOutlineVariant,
    error                = DifficultyCritical,
)

private val mushafCreamScheme = lightColorScheme(
    primary              = MushafPrimary,
    onPrimary            = MushafOnPrimary,
    primaryContainer     = MushafPrimaryContainer,
    onPrimaryContainer   = MushafOnPrimaryContainer,
    tertiary             = MushafTertiary,
    background           = MushafBg,
    onBackground         = MushafOnBg,
    surface              = MushafSurface,
    onSurface            = MushafOnBg,
    surfaceVariant       = MushafSurfaceVariant,
    onSurfaceVariant     = MushafOnSurfaceVariant,
    outline              = MushafOutline,
    outlineVariant       = MushafOutlineVariant,
    error                = MushafError,
)

private val midnightBlueScheme = darkColorScheme(
    primary              = MidnightPrimary,
    onPrimary            = MidnightOnPrimary,
    primaryContainer     = MidnightPrimaryContainer,
    onPrimaryContainer   = MidnightOnPrimaryContainer,
    tertiary             = MidnightTertiary,
    background           = MidnightBg,
    onBackground         = MidnightOnBg,
    surface              = MidnightSurface,
    onSurface            = MidnightOnBg,
    surfaceVariant       = MidnightSurfaceVariant,
    onSurfaceVariant     = MidnightOnSurfaceVariant,
    outline              = MidnightOutline,
    outlineVariant       = MidnightOutlineVariant,
    error                = DifficultyCritical,
)

// ── Theme composable ──────────────────────────────────────────────────────────

@Composable
fun QMemoTheme(
    themeKey: ThemeKey = ThemeKey.AUTO,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()

    val colorScheme = when (themeKey) {
        ThemeKey.AUTO         -> if (systemDark) modernDarkScheme else mushafCreamScheme
        ThemeKey.MODERN_DARK  -> modernDarkScheme
        ThemeKey.OLED_BLACK   -> oledBlackScheme
        ThemeKey.MUSHAF_CREAM -> mushafCreamScheme
        ThemeKey.MIDNIGHT_BLUE -> midnightBlueScheme
    }

    val isMushaf = themeKey == ThemeKey.MUSHAF_CREAM ||
                   (themeKey == ThemeKey.AUTO && !systemDark)

    val isArabic = LocalConfiguration.current.locales[0].language == "ar"

    // For Arabic locale, expand line heights so the script has breathing room.
    // For Mushaf Cream, use the serif base; otherwise use the modern sans-serif base.
    val typography = when {
        isArabic -> M3Typography(
            bodyLarge = TextStyle(
                fontFamily    = FontFamily.Default,
                fontWeight    = FontWeight.Normal,
                fontSize      = 16.sp,
                lineHeight    = 28.sp,
                letterSpacing = 0.5.sp
            ),
            bodyMedium = TextStyle(
                fontFamily    = FontFamily.Default,
                fontWeight    = FontWeight.Normal,
                fontSize      = 14.sp,
                lineHeight    = 24.sp,
                letterSpacing = 0.25.sp
            ),
            bodySmall = TextStyle(
                fontFamily    = FontFamily.Default,
                fontWeight    = FontWeight.Normal,
                fontSize      = 12.sp,
                lineHeight    = 20.sp,
                letterSpacing = 0.4.sp
            ),
            labelMedium = TextStyle(
                fontFamily    = FontFamily.Default,
                fontWeight    = FontWeight.Medium,
                fontSize      = 12.sp,
                lineHeight    = 18.sp,
                letterSpacing = 0.5.sp
            ),
            labelSmall = TextStyle(
                fontFamily    = FontFamily.Default,
                fontWeight    = FontWeight.Medium,
                fontSize      = 11.sp,
                lineHeight    = 17.sp,
                letterSpacing = 0.5.sp
            )
        )
        isMushaf -> MushafTypography
        else     -> Typography
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = typography,
        content     = content
    )
}
