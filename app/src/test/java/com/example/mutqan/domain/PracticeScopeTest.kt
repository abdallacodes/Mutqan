package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeScopeTest {

    private val total = 10

    /** Pages 1-6 memorised; 1-2 strong, 3-4 fair (not assessed), 5 weak, 6 not assessed with a self-rating of weak. */
    private val covered = BooleanArray(total) { it < 6 }
    private val states = Array(total) { i ->
        when (i + 1) {
            1, 2 -> PassageState.RELIABLE
            5 -> PassageState.NEEDS_ATTENTION
            else -> PassageState.NOT_ASSESSED
        }
    }
    private val selfRated = mapOf(6 to PlanTier.WEAK)

    private fun log(start: Int, end: Int, at: Long) =
        RevisionLog(startPage = start, endPage = end, timestamp = at, manualStability = 1f)

    private fun pages(scope: PracticeScope, logs: List<RevisionLog> = emptyList()) =
        scope.pages(covered, states, selfRated, logs, total)

    @Test
    fun `nothing chosen means every memorised page`() {
        assertEquals(setOf(1, 2, 3, 4, 5, 6), pages(PracticeScope()))
    }

    @Test
    fun `a level takes the pages at that level`() {
        assertEquals(setOf(5, 6), pages(PracticeScope(tiers = setOf(PlanTier.WEAK))))
        assertEquals(setOf(3, 4), pages(PracticeScope(tiers = setOf(PlanTier.FAIR))))
    }

    @Test
    fun `levels add up`() {
        assertEquals(setOf(3, 4, 5, 6), pages(PracticeScope(tiers = setOf(PlanTier.WEAK, PlanTier.FAIR))))
    }

    @Test
    fun `the last reviewed pages are the most recent ones`() {
        val logs = listOf(log(1, 2, at = 100), log(4, 4, at = 300), log(3, 3, at = 200))
        assertEquals(setOf(4, 3), pages(PracticeScope(lastReviewed = 2), logs))
    }

    @Test
    fun `last reviewed joins the chosen levels`() {
        val logs = listOf(log(1, 1, at = 100))
        assertEquals(setOf(5, 6, 1), pages(PracticeScope(tiers = setOf(PlanTier.WEAK), lastReviewed = 1), logs))
    }

    @Test
    fun `pages that are not memorised are never used`() {
        val logs = listOf(log(8, 9, at = 100))
        assertTrue(pages(PracticeScope(lastReviewed = 5), logs).isEmpty())
    }

    @Test
    fun `the choice survives being stored as a number`() {
        val scope = PracticeScope(tiers = setOf(PlanTier.WEAK, PlanTier.STRONG), lastReviewed = 20)
        assertEquals(scope, PracticeScope.fromMask(scope.tierMask, scope.lastReviewed))
    }
}
