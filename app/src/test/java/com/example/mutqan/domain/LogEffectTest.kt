package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogEffectTest {

    private val day = 86_400_000L

    private fun log(page: Int, outcome: RecallOutcome, dayNumber: Long, end: Int = page) = RevisionLog(
        startPage = page,
        endPage = end,
        timestamp = dayNumber * day,
        manualStability = outcome.quality,
        dateMillis = dayNumber * day,
        outcome = outcome
    )

    @Test
    fun `a first success on an unrated page moves nothing but gives an estimate`() {
        val effect = LogEffects.preview(emptyList(), log(5, RecallOutcome.INDEPENDENT, 100), null, emptyMap())
        assertEquals(1, effect.tracked)
        assertEquals(1, effect.before.getValue(PlanTier.FAIR))
        assertEquals(1, effect.after.getValue(PlanTier.FAIR))
        assertEquals(0, effect.up + effect.down)
        assertNull(effect.holdBefore)
        assertNotNull(effect.holdAfter)
    }

    @Test
    fun `a failed attempt moves a page the user rated strong down to weak`() {
        val effect = LogEffects.preview(
            emptyList(), log(5, RecallOutcome.UNABLE, 100), null, mapOf(5 to PlanTier.STRONG)
        )
        assertEquals(1, effect.before.getValue(PlanTier.STRONG))
        assertEquals(1, effect.after.getValue(PlanTier.WEAK))
        assertEquals(1, effect.down)
    }

    @Test
    fun `the second independent success on another day moves a page up`() {
        val history = listOf(log(5, RecallOutcome.INDEPENDENT, 100))
        val effect = LogEffects.preview(history, log(5, RecallOutcome.INDEPENDENT, 103), null, emptyMap())
        assertEquals(1, effect.up)
        assertEquals(1, effect.after.getValue(PlanTier.STRONG))
        assertTrue(effect.holdAfter!! > effect.holdBefore!!)
    }

    @Test
    fun `pages outside the memorised range are not counted`() {
        val covered = BooleanArray(604).also { for (i in 4..6) it[i] = true }
        val effect = LogEffects.preview(emptyList(), log(3, RecallOutcome.INDEPENDENT, 100, end = 8), covered, emptyMap())
        assertEquals(6, effect.total)
        assertEquals(3, effect.tracked)
    }

    @Test
    fun `reading only is flagged and changes nothing`() {
        val effect = LogEffects.preview(emptyList(), log(5, RecallOutcome.PRACTICE_ONLY, 100), null, emptyMap())
        assertTrue(effect.practiceOnly)
        assertEquals(0, effect.up + effect.down)
        assertNull(effect.holdAfter)
    }
}
