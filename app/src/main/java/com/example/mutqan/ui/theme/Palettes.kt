package com.example.mutqan.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.mutqan.data.Palette

/** The colours of one palette in one version (light or dark). Every palette has both. */
class PaletteColors(
    val bg: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val ink: Color,
    val inkSoft: Color,
    val rule: Color,
    val ruleSoft: Color,
    /** The action colour. */
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    /** The second accent, for marks and ornaments. */
    val tertiary: Color,
    val error: Color
)

private fun c(hex: Long) = Color(0xFF000000 or hex)

private val lapisLight = PaletteColors(
    bg = c(0xEEF2F6), surface = c(0xF5F8FB), surfaceVariant = c(0xE0E8F0), ink = c(0x14202E), inkSoft = c(0x526377),
    rule = c(0xB7C6D6), ruleSoft = c(0xD3DDE8), primary = c(0x1F4E8C), onPrimary = c(0xF5F8FB),
    primaryContainer = c(0xD2E1F3), onPrimaryContainer = c(0x0E2B52), tertiary = c(0x0F7F87), error = c(0xA33A2C)
)
private val lapisDark = PaletteColors(
    bg = c(0x0E1520), surface = c(0x141D2A), surfaceVariant = c(0x1B2636), ink = c(0xE3EAF3), inkSoft = c(0x93A3B8),
    rule = c(0x34455C), ruleSoft = c(0x222F42), primary = c(0x86B4EA), onPrimary = c(0x0E1520),
    primaryContainer = c(0x1E3552), onPrimaryContainer = c(0xCFE2F8), tertiary = c(0x5CC8CF), error = c(0xEE8D83)
)

private val duneLight = PaletteColors(
    bg = c(0xF2E8DA), surface = c(0xF8F0E4), surfaceVariant = c(0xE9DAC5), ink = c(0x2A1D14), inkSoft = c(0x75604D),
    rule = c(0xD2B995), ruleSoft = c(0xE2D0B3), primary = c(0xA24A27), onPrimary = c(0xFBF5EC),
    primaryContainer = c(0xF0D3C1), onPrimaryContainer = c(0x5A2410), tertiary = c(0x7A6420), error = c(0x9E2F2F)
)
private val duneDark = PaletteColors(
    bg = c(0x181210), surface = c(0x201815), surfaceVariant = c(0x2B211C), ink = c(0xF0E3D3), inkSoft = c(0xB09B86),
    rule = c(0x523D2F), ruleSoft = c(0x34271F), primary = c(0xE39A74), onPrimary = c(0x1B100B),
    primaryContainer = c(0x4A2A1A), onPrimaryContainer = c(0xF6D2BC), tertiary = c(0xD3B25E), error = c(0xEE8D83)
)

private val oliveLight = PaletteColors(
    bg = c(0xEEF0E4), surface = c(0xF5F6EC), surfaceVariant = c(0xE0E4D0), ink = c(0x1D2314), inkSoft = c(0x5C6449),
    rule = c(0xBDC5A0), ruleSoft = c(0xD6DBC0), primary = c(0x4B5F23), onPrimary = c(0xF5F6EC),
    primaryContainer = c(0xD9E2B8), onPrimaryContainer = c(0x26330D), tertiary = c(0x8A6A17), error = c(0xA33A2C)
)
private val oliveDark = PaletteColors(
    bg = c(0x11140D), surface = c(0x181C12), surfaceVariant = c(0x21261A), ink = c(0xE8ECD9), inkSoft = c(0xA0A98A),
    rule = c(0x424A2E), ruleSoft = c(0x2A3020), primary = c(0xA9C46A), onPrimary = c(0x11140D),
    primaryContainer = c(0x36431A), onPrimaryContainer = c(0xDCEBB0), tertiary = c(0xD5B45A), error = c(0xEE8D83)
)

private val plumLight = PaletteColors(
    bg = c(0xF4ECF0), surface = c(0xFAF3F6), surfaceVariant = c(0xEADCE4), ink = c(0x261520), inkSoft = c(0x6D5462),
    rule = c(0xD2B9C7), ruleSoft = c(0xE3D1DB), primary = c(0x6A2C5A), onPrimary = c(0xFAF3F6),
    primaryContainer = c(0xEBCFE3), onPrimaryContainer = c(0x3C0F31), tertiary = c(0x8C5A12), error = c(0xA33A2C)
)
private val plumDark = PaletteColors(
    bg = c(0x16101A), surface = c(0x1D1522), surfaceVariant = c(0x271C2E), ink = c(0xF0E4EE), inkSoft = c(0xB49FB0),
    rule = c(0x4E3A52), ruleSoft = c(0x31243A), primary = c(0xDDA6CF), onPrimary = c(0x16101A),
    primaryContainer = c(0x4B2442), onPrimaryContainer = c(0xF6D6EC), tertiary = c(0xD9B25E), error = c(0xEE8D83)
)

/** Neutral greys with a slate accent; its dark version is true black, which saves power on OLED screens. */
private val graphiteLight = PaletteColors(
    bg = c(0xFAFAF8), surface = c(0xFFFFFF), surfaceVariant = c(0xEDEDEA), ink = c(0x111111), inkSoft = c(0x5E5E5A),
    rule = c(0xC9C9C4), ruleSoft = c(0xE2E2DE), primary = c(0x2F3E4A), onPrimary = c(0xFAFAF8),
    primaryContainer = c(0xDDE3E8), onPrimaryContainer = c(0x121B22), tertiary = c(0x6B5B2E), error = c(0xA33A2C)
)
private val graphiteDark = PaletteColors(
    bg = c(0x000000), surface = c(0x0B0B0B), surfaceVariant = c(0x151515), ink = c(0xECECEA), inkSoft = c(0x9A9A96),
    rule = c(0x333333), ruleSoft = c(0x1F1F1F), primary = c(0xB8C7D4), onPrimary = c(0x000000),
    primaryContainer = c(0x26323C), onPrimaryContainer = c(0xDCE6EE), tertiary = c(0xCDB26A), error = c(0xEE8D83)
)

/** The colours of [palette], or null for Paper, which has its own hand-tuned schemes in Theme.kt. */
fun paletteColors(palette: Palette, dark: Boolean): PaletteColors? = when (palette) {
    Palette.PAPER -> null
    Palette.LAPIS -> if (dark) lapisDark else lapisLight
    Palette.DUNE -> if (dark) duneDark else duneLight
    Palette.OLIVE -> if (dark) oliveDark else oliveLight
    Palette.PLUM -> if (dark) plumDark else plumLight
    Palette.GRAPHITE -> if (dark) graphiteDark else graphiteLight
}

/** A Material colour scheme from [p]; the container steps are blended between its surface and surface variant. */
fun PaletteColors.toScheme(dark: Boolean): ColorScheme {
    val lowest = if (dark) lerp(bg, Color.Black, 0.35f) else lerp(surface, Color.White, 0.5f)
    val container = lerp(surface, surfaceVariant, 0.35f)
    val high = lerp(surface, surfaceVariant, 0.7f)
    return if (dark) {
        darkColorScheme(
            primary = primary, onPrimary = onPrimary, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            secondary = inkSoft, onSecondary = bg, secondaryContainer = surfaceVariant, onSecondaryContainer = ink,
            tertiary = tertiary, background = bg, onBackground = ink, surface = surface, onSurface = ink,
            surfaceVariant = surfaceVariant, onSurfaceVariant = inkSoft, outline = rule, outlineVariant = ruleSoft, error = error,
            surfaceTint = surface, surfaceBright = surfaceVariant, surfaceDim = bg,
            surfaceContainerLowest = lowest, surfaceContainerLow = surface, surfaceContainer = container,
            surfaceContainerHigh = high, surfaceContainerHighest = surfaceVariant
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = onPrimary, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            secondary = inkSoft, onSecondary = bg, secondaryContainer = surfaceVariant, onSecondaryContainer = ink,
            tertiary = tertiary, background = bg, onBackground = ink, surface = surface, onSurface = ink,
            surfaceVariant = surfaceVariant, onSurfaceVariant = inkSoft, outline = rule, outlineVariant = ruleSoft, error = error,
            surfaceTint = surface, surfaceBright = surface, surfaceDim = surfaceVariant,
            surfaceContainerLowest = lowest, surfaceContainerLow = surface, surfaceContainer = container,
            surfaceContainerHigh = high, surfaceContainerHighest = surfaceVariant
        )
    }
}

/** Background, action colour and second accent of [palette], for its swatch in Settings. */
fun paletteSwatch(palette: Palette, dark: Boolean): Triple<Color, Color, Color> =
    paletteColors(palette, dark)?.let { Triple(it.bg, it.primary, it.tertiary) }
        ?: if (dark) Triple(NightBg, NightGold, NightGold) else Triple(PaperBg, PaperGreen, PaperGoldText)
