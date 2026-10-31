package com.example.mutqan.ui.mushaf

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.mutqan.R

/** What the Mushaf page looks like, apart from the rest of the app. [AUTO] follows the app: parchment, or night when the app is dark. */
enum class MushafTheme(val labelRes: Int) {
    AUTO(R.string.mushaf_theme_auto),
    PARCHMENT(R.string.mushaf_theme_parchment),
    WHITE(R.string.mushaf_theme_white),
    SEPIA(R.string.mushaf_theme_sepia),
    NIGHT(R.string.mushaf_theme_night);

    companion object {
        fun fromName(name: String?): MushafTheme = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/** The colours of the Mushaf page and its floating bar. [dark] says whether the page is dark, for the status bar icons. */
class MushafColors(
    val bg: Color,
    val ink: Color,
    val muted: Color,
    val scrim: Color,
    val spine: Color,
    val dark: Boolean
)

private val parchment = MushafColors(
    bg = Color(0xFFE8E0D2), ink = Color(0xFF353028), muted = Color(0xFF6B655C),
    scrim = Color(0xE8E8E0D2), spine = Color(0x145C5346), dark = false
)
private val white = MushafColors(
    bg = Color(0xFFFAFAF7), ink = Color(0xFF1A1A1A), muted = Color(0xFF6A6A66),
    scrim = Color(0xE8FAFAF7), spine = Color(0x14000000), dark = false
)
private val sepia = MushafColors(
    bg = Color(0xFFF0E2C3), ink = Color(0xFF3B2D1A), muted = Color(0xFF7A6A50),
    scrim = Color(0xE8F0E2C3), spine = Color(0x145C4A2A), dark = false
)
private val night = MushafColors(
    bg = Color(0xFF111315), ink = Color(0xFFD9D3C5), muted = Color(0xFF8F8A7E),
    scrim = Color(0xE8111315), spine = Color(0x14FFFFFF), dark = true
)

fun MushafTheme.colors(appIsDark: Boolean): MushafColors = when (this) {
    MushafTheme.AUTO -> if (appIsDark) night else parchment
    MushafTheme.PARCHMENT -> parchment
    MushafTheme.WHITE -> white
    MushafTheme.SEPIA -> sepia
    MushafTheme.NIGHT -> night
}

val LocalMushafColors = compositionLocalOf { parchment }
