package com.example.qmemo.ui.heatmap

import com.example.qmemo.domain.PageStability

/**
 * Combines a page's memory-decay [stability] with the Surah name(s) displayed on it.
 *
 * Most pages are single-Surah; transition pages (where one Surah ends and the next begins)
 * carry exactly two names.  The [surahLabel] property formats them for display.
 */
data class PageWithSurahs(
    val stability:  PageStability,
    /** 1 entry for a normal page, 2 entries for a Surah-transition page. */
    val surahNames: List<String>
) {
    /**
     * Short display label:
     *  - Single Surah  → "Al-Baqarah"
     *  - Two Surahs    → "Al-Anfal / At-Tawbah"
     *  - No info       → ""
     */
    val surahLabel: String
        get() = surahNames.joinToString(" / ")
}

/**
 * Selection state for the Quick-Status dialog:
 * carries both the page number (needed by [logPage]) and the surah label
 * (shown as context in the dialog title).
 */
data class PageSelection(
    val page:       Int,
    val surahLabel: String
)
