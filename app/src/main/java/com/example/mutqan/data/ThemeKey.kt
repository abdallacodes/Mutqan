package com.example.mutqan.data

/**
 * Which look the app uses. There is one theme, Paper, in a light and a dark version.
 *
 * [AUTO]  -> follows the system dark/light mode setting.
 * [LIGHT] -> warm paper with ink text and a deep green accent.
 * [DARK]  -> warm near-black with parchment text and a gold accent.
 */
enum class ThemeKey {
    AUTO,
    LIGHT,
    DARK;

    companion object {
        /** Reads a stored name; the four earlier themes map to the nearest of the two versions. */
        fun fromString(name: String): ThemeKey = when (name) {
            "LIGHT", "MUSHAF_CREAM" -> LIGHT
            "DARK", "MODERN_DARK", "OLED_BLACK", "MIDNIGHT_BLUE" -> DARK
            else -> AUTO
        }
    }
}

/**
 * The colours the app uses, apart from light or dark ([ThemeKey]). Each palette has a light and a dark version.
 * [PAPER] is the original: cream and green, parchment and gold.
 */
enum class Palette {
    PAPER,
    LAPIS,
    DUNE,
    OLIVE,
    PLUM,
    GRAPHITE;

    companion object {
        fun fromString(name: String?): Palette = entries.firstOrNull { it.name == name } ?: PAPER
    }
}
