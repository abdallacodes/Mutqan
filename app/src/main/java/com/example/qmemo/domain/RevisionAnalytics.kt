package com.example.qmemo.domain

import kotlin.math.ceil

object RevisionAnalytics {

    data class AnalyticsReport(
        val debtGrowthRate: Float,    // Pages per day falling below 50%
        val revisionVelocity: Float,  // Pages revised per day (last 7 days)
        val recoveryDays: Int,         // Days to turn everything green
        val sessionsPerDayNeeded: Int // Sessions per day needed for recovery
    )

    /**
     * Calculates analytics based on current state and projection.
     */
    fun computeAnalytics(
        state: MemoryEngine.MemoryState,
        rNow: FloatArray,
        rIn7Days: FloatArray
    ): AnalyticsReport {
        // 1. Debt Growth Rate: how many pages drop < 0.5 in 7 days
        // We use a weighted sum of the retrievability drop to make it smoother
        // and ensure it's not always 0.0 unless stability is perfect.
        var totalDrop = 0f
        var trackedCount = 0
        for (i in 0 until MemoryEngine.TOTAL_PAGES) {
            if (state.stability[i] == 0f) continue
            trackedCount++
            // Calculate how much retrievability is lost over 7 days
            val drop = (rNow[i] - rIn7Days[i]).coerceAtLeast(0f)
            totalDrop += drop
        }

        // Convert the total retrievability drop into an equivalent "page debt" growth.
        // If 10 pages each drop by 0.1, it's equivalent to 1 page falling into full debt.
        val growthRate = if (trackedCount > 0) totalDrop / 7f else 0f

        // 2. Revision Velocity: (simulated or historical)
        val velocity = 5f

        // 3. Recovery: How many pages < 0.7 (Stable threshold)
        var unstableCount = 0
        for (i in 0 until MemoryEngine.TOTAL_PAGES) {
            if (state.stability[i] > 0f && rNow[i] < 0.7f) unstableCount++
        }

        val daysToRecover = if (velocity > growthRate) {
            ceil(unstableCount / (velocity - growthRate)).toInt().coerceIn(1, 365)
        } else {
            -1 // Never recovers at current pace
        }

        return AnalyticsReport(
            debtGrowthRate = growthRate,
            revisionVelocity = velocity,
            recoveryDays = daysToRecover,
            sessionsPerDayNeeded = ceil(unstableCount / 30f).toInt().coerceAtLeast(1) // 30-day target
        )
    }
}
