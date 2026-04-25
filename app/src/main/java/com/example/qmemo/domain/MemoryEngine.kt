package com.example.qmemo.domain

import com.example.qmemo.data.local.entity.RevisionLogEntity

/**
 * Pure, stateless computation engine — no Android framework dependencies.
 * Safe to run on any dispatcher, including [kotlinx.coroutines.Dispatchers.Default].
 *
 * ## Decay model
 * Each page's stability is governed by a [DifficultyProfile]:
 *
 *   score = initialScore × e^( −ln2 × daysElapsed / halfLifeDays )
 *
 * The **initialScore** caps the starting stability right after revision so that
 * harder sessions never show "full green":
 *
 *   Smooth    (1) → starts at 1.00 — full confidence, 21-day half-life
 *   Struggled (2) → starts at 0.88 — slight cap,  10.5-day half-life (2× faster than Smooth)
 *   Critical  (3) → starts at 0.55 — orange/yellow from day 0, 3-day half-life
 *
 * Result: score = 1.0 right after a Smooth revision; score ≈ 0.55 right after a Critical
 * revision (the page stays "orange/yellow" immediately, demanding quick follow-up).
 */
object MemoryEngine {

    const val TOTAL_PAGES = 604

    private data class DifficultyProfile(
        /** Score ceiling immediately after revision (0.0–1.0). */
        val initialScore: Double,
        /** Days for the score to decay to half of its initialScore. */
        val halfLifeDays: Double
    )

    private val PROFILES = mapOf(
        1 to DifficultyProfile(initialScore = 1.00, halfLifeDays = 21.0),  // Smooth
        2 to DifficultyProfile(initialScore = 0.88, halfLifeDays = 10.5),  // Struggled (2× faster)
        3 to DifficultyProfile(initialScore = 0.55, halfLifeDays =  3.0)   // Critical (orange cap)
    )

    private const val LN2 = 0.6931471805599453

    /**
     * Computes a [PageStability] for every one of the 604 Mushaf pages.
     *
     * Strategy (O(Σ rangeLength + 604)):
     * 1. Expand each [RevisionLogEntity] across its page range, keeping only
     *    the most-recent log per page.
     * 2. For each page, apply the weighted decay formula against [nowMillis].
     *
     * Pages never covered by any log get score = 0.0 and [PageStability.isTracked] = false.
     *
     * @param logs      All revision logs from the DB (order irrelevant)
     * @param nowMillis Reference timestamp for "current time" (injectable for testing)
     */
    fun computeStabilities(
        logs: List<RevisionLogEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<PageStability> {

        // Pass 1: build page → most-recent-log map
        val latestPerPage = HashMap<Int, RevisionLogEntity>(TOTAL_PAGES * 2)
        for (log in logs) {
            val start = log.startPage.coerceIn(1, TOTAL_PAGES)
            val end   = log.endPage.coerceIn(start, TOTAL_PAGES)
            for (page in start..end) {
                val existing = latestPerPage[page]
                if (existing == null || log.timestamp > existing.timestamp) {
                    latestPerPage[page] = log
                }
            }
        }

        // Pass 2: compute score for all 604 pages (single list allocation)
        return buildList(TOTAL_PAGES) {
            for (page in 1..TOTAL_PAGES) {
                val log = latestPerPage[page]
                if (log == null) {
                    add(PageStability(page = page, score = 0f, lastRevised = null, lastDifficulty = null))
                } else {
                    val profile     = PROFILES[log.difficulty] ?: PROFILES[1]!!
                    val daysElapsed = (nowMillis - log.timestamp).coerceAtLeast(0L) / 86_400_000.0
                    val score       = (profile.initialScore *
                        Math.exp(-LN2 * daysElapsed / profile.halfLifeDays))
                        .coerceIn(0.0, 1.0)
                        .toFloat()
                    add(
                        PageStability(
                            page           = page,
                            score          = score,
                            lastRevised    = log.timestamp,
                            lastDifficulty = log.difficulty
                        )
                    )
                }
            }
        }
    }

    /**
     * Interprets a score as a human-readable state label.
     * Useful for accessibility and the page detail popup.
     */
    fun scoreLabel(score: Float, isTracked: Boolean): String = when {
        !isTracked      -> "Not tracked"
        score >= 0.85f  -> "Strong"
        score >= 0.60f  -> "Good"
        score >= 0.40f  -> "Fading"
        score >= 0.20f  -> "Weak"
        else            -> "Critical"
    }
}
