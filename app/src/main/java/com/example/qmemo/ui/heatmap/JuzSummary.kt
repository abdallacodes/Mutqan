package com.example.qmemo.ui.heatmap

import com.example.qmemo.domain.PageStability

/**
 * Aggregated memory-health data for one Juz, derived from the global stabilities list.
 *
 * @param juzId          1–30
 * @param pageStabilities Ordered list of [PageStability] for every page in this Juz.
 * @param healthPercent  Average stability of *tracked* pages, 0–100. Returns 0 if no pages
 *                       have been revised yet.
 * @param trackedCount   How many pages in this Juz have been revised at least once.
 */
data class JuzSummary(
    val juzId:           Int,
    val pageStabilities: List<PageStability>,
    val healthPercent:   Int,
    val trackedCount:    Int
) {
    val totalPages: Int get() = pageStabilities.size
}
