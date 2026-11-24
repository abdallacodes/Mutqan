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

    private val day = 86_400_000L

    /** 15 June 2026, noon UTC. */
    private val now = 1_781_524_800_000L

    private fun pages(scope: PracticeScope, logs: List<RevisionLog> = emptyList()) =
        scope.pages(covered, states, selfRated, logs, total, now)

    private fun place(vararg pages: Int) = ScopePlace(ScopePlace.Kind.PAGES, pages.toSet(), pages.toSet())

    @Test
    fun `a place keeps the memorised pages in it`() {
        assertEquals(setOf(4, 5, 6), pages(PracticeScope(place = place(4, 5, 6, 7, 8))))
        // With a level too: the weak pages of the place.
        assertEquals(setOf(5, 6), pages(PracticeScope(tiers = setOf(PlanTier.WEAK), place = place(4, 5, 6, 7))))
    }

    @Test
    fun `a place with none of it memorised gives its pages as they are`() {
        assertEquals(setOf(8, 9), pages(PracticeScope(place = place(8, 9))))
    }

    @Test
    fun `a place with what was reviewed lately keeps only those`() {
        val logs = listOf(log(1, 2, now - day), log(7, 8, now - 2 * day))
        // Reviewed this week, in pages 7-9: page 7 is not memorised, so nothing is left.
        assertEquals(emptySet<Int>(), pages(PracticeScope(recentAmount = 1, recentUnit = RecencyUnit.WEEKS, place = place(7, 8, 9)), logs))
        assertEquals(setOf(1, 2), pages(PracticeScope(recentAmount = 1, recentUnit = RecencyUnit.WEEKS, place = place(1, 2, 3)), logs))
    }

    @Test
    fun `a place is not every page`() {
        assertTrue(!PracticeScope(place = place(1)).isAll)
        assertTrue(PracticeScope().isAll)
    }

    @Test
    fun `a place is kept as runs and read back`() {
        assertEquals("1-3,7,10-12", ScopePlace.encode(setOf(12, 1, 2, 3, 7, 10, 11)))
        assertEquals(setOf(1, 2, 3, 7, 10, 11, 12), ScopePlace.decode("1-3,7,10-12"))
        assertEquals(emptySet<Int>(), ScopePlace.decode("x,5-2,"))
        val kept = ScopePlace.fromStored(ScopePlace.Kind.SURAHS.code, "2,3", "2-76")
        assertEquals(ScopePlace(ScopePlace.Kind.SURAHS, setOf(2, 3), (2..76).toSet()), kept)
        assertEquals(null, ScopePlace.fromStored(0, "2", "2-49"))
        assertEquals(null, ScopePlace.fromStored(ScopePlace.Kind.JUZ.code, "", "1-21"))
    }

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
        assertEquals(setOf(4, 3), pages(PracticeScope(recentAmount = 2), logs))
    }

    @Test
    fun `levels narrow the pages reached`() {
        val logs = listOf(log(1, 1, at = 100), log(5, 5, at = 200), log(3, 3, at = 300))
        assertEquals(setOf(5), pages(PracticeScope(tiers = setOf(PlanTier.WEAK), recentAmount = 3), logs))
        assertEquals(setOf(3, 5), pages(PracticeScope(tiers = setOf(PlanTier.WEAK, PlanTier.FAIR), recentAmount = 2), logs))
    }

    @Test
    fun `levels narrow a time span too`() {
        val logs = listOf(log(1, 3, at = now - day))
        assertEquals(setOf(3), pages(PracticeScope(tiers = setOf(PlanTier.FAIR), recentAmount = 1, recentUnit = RecencyUnit.WEEKS), logs))
    }

    @Test
    fun `pages that are not memorised are never used`() {
        val logs = listOf(log(8, 9, at = 100))
        assertTrue(pages(PracticeScope(recentAmount = 5), logs).isEmpty())
    }

    @Test
    fun `the preview counts the levels among the pages the reach gives`() {
        val p = PracticeScope().preview(covered, states, selfRated, emptyList(), total, now)
        assertEquals(6, p.reached)
        assertEquals(6, p.total)
        assertEquals(mapOf(PlanTier.WEAK to 2, PlanTier.FAIR to 2, PlanTier.STRONG to 2), p.byTier)
    }

    @Test
    fun `the preview total follows the chosen levels but the counts stay`() {
        val p = PracticeScope(tiers = setOf(PlanTier.WEAK, PlanTier.STRONG)).preview(covered, states, selfRated, emptyList(), total, now)
        assertEquals(6, p.reached)
        assertEquals(4, p.total)
        assertEquals(2, p.byTier.getValue(PlanTier.FAIR))
    }

    @Test
    fun `the preview of a short reach counts only those pages`() {
        val logs = listOf(log(1, 1, at = 100), log(5, 5, at = 200), log(3, 3, at = 300))
        val p = PracticeScope(recentAmount = 2).preview(covered, states, selfRated, logs, total, now)
        assertEquals(2, p.reached)
        assertEquals(1, p.byTier.getValue(PlanTier.FAIR))
        assertEquals(1, p.byTier.getValue(PlanTier.WEAK))
        assertEquals(0, p.byTier.getValue(PlanTier.STRONG))
    }

    @Test
    fun `the preview total always equals the number of pages`() {
        val logs = listOf(log(1, 4, at = now - day))
        val scopes = listOf(
            PracticeScope(),
            PracticeScope(tiers = setOf(PlanTier.FAIR)),
            PracticeScope(recentAmount = 3),
            PracticeScope(tiers = setOf(PlanTier.WEAK, PlanTier.FAIR), recentAmount = 1, recentUnit = RecencyUnit.WEEKS)
        )
        for (s in scopes) {
            assertEquals(s.pages(covered, states, selfRated, logs, total, now).size, s.preview(covered, states, selfRated, logs, total, now).total)
        }
    }

    @Test
    fun `nothing reached previews as zero`() {
        val p = PracticeScope(recentAmount = 3, recentUnit = RecencyUnit.DAYS).preview(covered, states, selfRated, emptyList(), total, now)
        assertEquals(0, p.total)
        assertEquals(0, p.reached)
    }

    @Test
    fun `the mask marks exactly the allowed pages`() {
        val mask = PracticeScope(tiers = setOf(PlanTier.WEAK)).mask(covered, states, selfRated, emptyList(), total, now)!!
        assertEquals(total, mask.size)
        assertEquals(setOf(5, 6), (1..total).filter { mask[it - 1] }.toSet())
    }

    @Test
    fun `the mask of no choice is what is memorised`() {
        assertEquals(covered.toList(), PracticeScope().mask(covered, states, selfRated, emptyList(), total, now)!!.toList())
        assertEquals(null, PracticeScope().mask(null, states, selfRated, emptyList(), total, now))
    }

    @Test
    fun `the choice survives being stored as a number`() {
        val scope = PracticeScope(tiers = setOf(PlanTier.WEAK, PlanTier.STRONG), recentAmount = 20, recentUnit = RecencyUnit.MONTHS)
        assertEquals(scope, PracticeScope.fromStored(scope.tierMask, scope.recentAmount, scope.recentUnit.code))
    }

    @Test
    fun `days look back from now`() {
        val logs = listOf(log(1, 1, at = now - 2 * day), log(2, 2, at = now - 10 * day))
        assertEquals(setOf(1), pages(PracticeScope(recentAmount = 7, recentUnit = RecencyUnit.DAYS), logs))
        assertEquals(setOf(1, 2), pages(PracticeScope(recentAmount = 11, recentUnit = RecencyUnit.DAYS), logs))
    }

    @Test
    fun `weeks months and years are whole spans`() {
        val logs = listOf(log(1, 1, at = now - 10 * day), log(2, 2, at = now - 40 * day), log(3, 3, at = now - 400 * day))
        assertEquals(setOf(1), pages(PracticeScope(recentAmount = 2, recentUnit = RecencyUnit.WEEKS), logs))
        assertEquals(setOf(1, 2), pages(PracticeScope(recentAmount = 2, recentUnit = RecencyUnit.MONTHS), logs))
        assertEquals(setOf(1, 2, 3), pages(PracticeScope(recentAmount = 2, recentUnit = RecencyUnit.YEARS), logs))
    }

    @Test
    fun `a time span takes every page of a log inside it`() {
        val logs = listOf(log(1, 4, at = now - day))
        assertEquals(setOf(1, 2, 3, 4), pages(PracticeScope(recentAmount = 3, recentUnit = RecencyUnit.DAYS), logs))
    }
}
