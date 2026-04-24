package com.example.qmemo.data

/**
 * Identifies which color scheme the app should use.
 *
 * [AUTO]          → follows the system dark/light mode setting.
 * [MODERN_DARK]   → deep charcoal background with mint-green accent.
 * [OLED_BLACK]    → true black background with emerald-green accent (battery-saving).
 * [MUSHAF_CREAM]  → warm parchment background with deep-maroon accent (light theme).
 * [MIDNIGHT_BLUE] → deep navy background with warm-gold accent.
 */
enum class ThemeKey {
    AUTO,
    MODERN_DARK,
    OLED_BLACK,
    MUSHAF_CREAM,
    MIDNIGHT_BLUE;

    companion object {
        fun fromString(name: String): ThemeKey =
            entries.firstOrNull { it.name == name } ?: AUTO
    }
}
