package com.example.qmemo.ui.heatmap

import androidx.compose.runtime.Immutable

/**
 * Aggregated memory-health data for one Juz, derived from the global stabilities list.
 *
 * @param minimapColors Packed [androidx.compose.ui.graphics.Color] values (`.value`) for each
 *                      page tile in display order — computed off the main thread so the grid
 *                      avoids per-cell color work during composition.
 */
@Immutable
data class JuzSummary(
    val juzId:         Int,
    val totalPages:    Int,
    val minimapColors: List<ULong>,
    val healthPercent: Int,
    val trackedCount:  Int,
    /** Packed [androidx.compose.ui.graphics.Color] for % / bar when tracked; `null` → theme. */
    val healthTone:    ULong? = null,
    /** Packed border when tracked; `null` → theme outline. */
    val borderTone:    ULong? = null
)
