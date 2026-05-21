package com.example.qmemo.domain

import com.example.qmemo.data.local.dao.PageSimilarityLink
import com.example.qmemo.data.local.entity.RevisionLogEntity
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Advanced Memory Engine using FSRS (Free Spaced Repetition Scheduler) logic.
 * 
 * Optimized for performance using primitive arrays for sub-millisecond 
 * computations across 604 pages.
 */
object MemoryEngine {

    const val TOTAL_PAGES = 604
    private const val MS_PER_DAY = 86_400_000f

    /**
     * Computed state of the memory model for all pages.
     */
    class MemoryState(
        val stability: FloatArray, // S: days until retrievability drops to 90%
        val lastRevisionTimestamps: LongArray
    )

    /**
     * Replays revision history to find the current Stability (S) for every page.
     * Includes Semantic Interference logic from Mutashabihat.
     * 
     * @param logs All revision logs, sorted by timestamp
     * @param similarityLinks Map of page to its semantically similar pages
     */
    fun computeCurrentState(
        logs: List<RevisionLogEntity>,
        similarityLinks: Map<Int, List<Int>>
    ): MemoryState {
        val stability = FloatArray(TOTAL_PAGES) { 0f }
        val lastRevisions = LongArray(TOTAL_PAGES) { 0L }

        val sortedLogs = logs.sortedBy { it.timestamp }

        for (log in sortedLogs) {
            val start = log.startPage.coerceIn(1, TOTAL_PAGES)
            val end   = log.endPage.coerceIn(start, TOTAL_PAGES)
            val logTime = log.timestamp

            for (page in start..end) {
                val idx = page - 1
                val oldS = stability[idx]
                val oldT = lastRevisions[idx]
                
                val currentQuality = log.manualStability
                
                // Map quality to FSRS ratings
                // 1=Smooth (q >= 0.8), 2=Struggled (0.4 <= q < 0.8), 3=Critical (q < 0.4)
                val rating = when {
                    currentQuality >= 0.8f -> 1
                    currentQuality >= 0.4f -> 2
                    else -> 3
                }

                if (oldS == 0f || oldT == 0L) {
                    // 1. Initial tracking: use cubic baseline
                    // q=1.0 -> 50 days, q=0.7 -> 17 days, q=0.5 -> 6.25 days
                    val baseS = currentQuality.toDouble().pow(3.0).toFloat() * 50f
                    stability[idx] = baseS.coerceAtLeast(0.1f)
                } else {
                    // 2. Cumulative tracking: use FSRS growth
                    val t = (logTime - oldT) / MS_PER_DAY
                    val r = FSRSModel.calculateRetrievability(t.coerceAtLeast(0f), oldS)
                    
                    stability[idx] = FSRSModel.nextStability(oldS, r, rating)
                }
                
                lastRevisions[idx] = logTime

                // 3. UI Sync: adjust timestamp so R matches current manual quality
                // This ensures the "Health" color immediately reflects the user's input.
                if (currentQuality < 1.0f && currentQuality > 0f) {
                    val virtualT = 9f * stability[idx] * (1f / currentQuality - 1f)
                    lastRevisions[idx] = logTime - (virtualT * MS_PER_DAY).toLong()
                }

                // 4. Semantic Interference
                if (currentQuality < 0.7f) {
                    similarityLinks[page]?.forEach { similarPage ->
                        val sIdx = similarPage - 1
                        if (stability[sIdx] > 0f) {
                            // Drop stability by 15% to reflect potential confusion
                            stability[sIdx] *= 0.85f
                        }
                    }
                }
            }
        }

        return MemoryState(stability, lastRevisions)
    }

    /**
     * Projects the Retrievability (R) of each page into the future.
     * Optimized O(604) for real-time slider UI (60fps).
     * 
     * @param state Current computed stability state
     * @param projectionDays Days into the future to forecast (0 = now)
     * @param nowMillis Current time reference
     */
    fun projectRetrievability(
        state: MemoryState,
        projectionDays: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): FloatArray {
        val retrievability = FloatArray(TOTAL_PAGES)
        val projectionMillis = projectionDays * 86_400_000L
        val targetTime = nowMillis + projectionMillis

        for (i in 0 until TOTAL_PAGES) {
            val s = state.stability[i]
            val lastT = state.lastRevisionTimestamps[i]
            
            if (s == 0f || lastT == 0L) {
                retrievability[i] = 0f
            } else {
                val t = (targetTime - lastT) / MS_PER_DAY
                retrievability[i] = FSRSModel.calculateRetrievability(t, s).coerceIn(0f, 1f)
            }
        }
        return retrievability
    }

    fun scoreLabel(r: Float, isTracked: Boolean): String = when {
        !isTracked   -> "Not tracked"
        r >= 0.90f   -> "Mastered"
        r >= 0.70f   -> "Stable"
        r >= 0.50f   -> "Fading"
        r >= 0.30f   -> "Weak"
        else         -> "Critical"
    }
}
