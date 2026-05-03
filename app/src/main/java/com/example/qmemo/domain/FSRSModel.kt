package com.example.qmemo.domain

import kotlin.math.ln
import kotlin.math.pow

/**
 * Simplified FSRS (Free Spaced Repetition Scheduler) implementation for Quranic memorization.
 * 
 * S: Stability (days until retrievability drops to 90%)
 * R: Retrievability (estimated probability of successful recall)
 * D: Difficulty (intrinsic difficulty of the material)
 */
object FSRSModel {

    // Default parameters (could be tuned in the future)
    private val w = floatArrayOf(
        0.4f, 0.6f, 2.4f, 5.8f,  // Initial stability for each rating
        4.9f,                    // Difficulty increase for 'Struggled'
        0.94f, 0.73f,            // Stability boost factors
        0.05f, 1.0f,             // Stability boost modulation
        0.5f, 2.0f               // Decay factors
    )

    /**
     * $R = (1 + \frac{t}{9 \cdot S})^{-1}$
     * 
     * @param t days since last revision
     * @param s current stability
     */
    fun calculateRetrievability(t: Float, s: Float): Float {
        if (s <= 0f) return 0f
        return (1f + t / (9f * s)).pow(-1f)
    }

    /**
     * Initial stability based on rating (1=Smooth, 2=Struggled, 3=Critical)
     */
    fun initialStability(rating: Int): Float {
        return when (rating) {
            1 -> w[3] // Smooth
            2 -> w[2] // Struggled
            3 -> w[1] // Critical
            else -> w[0]
        }
    }

    /**
     * Boost stability after a successful revision.
     * accounts for 'interval'—if a user revises early (high R), boost is smaller.
     * 
     * @param s current stability
     * @param r retrievability at time of revision
     * @param rating 1=Smooth, 2=Struggled, 3=Critical
     */
    fun nextStability(s: Float, r: Float, rating: Int): Float {
        if (rating == 3) {
            // Failure/Critical: significant drop
            return s * 0.2f 
        }
        
        val boost = if (rating == 1) 1.5f else 1.1f
        // Smaller boost if R is high (revised early), larger if R is low (revised late)
        val rFactor = (2.0f - r).coerceIn(1.0f, 2.0f) 
        
        return (s * boost * rFactor).coerceAtMost(3650f) // Cap at 10 years
    }
}
