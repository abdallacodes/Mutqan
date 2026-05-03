package com.example.qmemo.domain

/**
 * The computed memory state of a single Mushaf page.
 *
 * @param page           Madinah Mushaf page number (1–604)
 * @param score          0.0 = fully decayed / never tracked · 1.0 = just revised
 * @param lastRevised    Epoch millis of the most recent revision covering this page; null if never revised
 */
data class PageStability(
    val page: Int,
    val score: Float,
    val lastRevised: Long?
) {
    /** True when the page has been revised at least once and is actively tracked. */
    val isTracked: Boolean get() = lastRevised != null
}
