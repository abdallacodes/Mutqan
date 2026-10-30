package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RevisionPlannerTest {

    private val day = 86_400_000L
    private val utc: (Long) -> Int = { 0 }

    // Juz 1 = pages 1..20, juz 2 = pages 21..40, juz 3 = pages 41..60; surah 1 = 1, surah 2 = 2..50.
    private val geometry = PlanGeometry(
        juzPages = mapOf(1 to (1..20).toList(), 2 to (21..40).toList(), 3 to (41..60).toList()),
        surahPages = listOf(1..1, 2..50)
    )

    private fun covered(vararg pages: IntRange): BooleanArray =
        BooleanArray(604).also { arr -> pages.forEach { r -> r.forEach { arr[it - 1] = true } } }

    private val noStates = Array(604) { PassageState.NOT_ASSESSED }

    private fun log(start: Int, end: Int, onDay: Long, outcome: RecallOutcome = RecallOutcome.INDEPENDENT) = RevisionLog(
        startPage = start,
        endPage = end,
        timestamp = onDay * day,
        manualStability = outcome.quality,
        dateMillis = onDay * day,
        outcome = outcome
    )

    private fun plan(unit: PlanUnit = PlanUnit.JUZ, amount: Int = 1, rest: Set<Int> = emptySet(), order: PlanOrder = PlanOrder.ROTATION, paused: Boolean = false) =
        RevisionPlan(name = "p", unit = unit, amountPerDay = amount, restDays = rest, order = order, paused = paused)

    // ── Items ─────────────────────────────────────────────────────────────────

    @Test
    fun `no coverage means no portions`() {
        assertTrue(RevisionPlanner.buildItems(PlanUnit.PAGE, null, geometry).isEmpty())
    }

    @Test
    fun `juz portions span only the covered pages`() {
        val items = RevisionPlanner.buildItems(PlanUnit.JUZ, covered(5..25), geometry)
        assertEquals(
            listOf(PlanItem(PlanUnit.JUZ, 1, 5, 20), PlanItem(PlanUnit.JUZ, 2, 21, 25)),
            items
        )
    }

    @Test
    fun `page and surah portions follow the coverage`() {
        assertEquals(3, RevisionPlanner.buildItems(PlanUnit.PAGE, covered(10..12), geometry).size)
        val surahs = RevisionPlanner.buildItems(PlanUnit.SURAH, covered(40..41), geometry)
        assertEquals(listOf(PlanItem(PlanUnit.SURAH, 2, 2, 50)), surahs)
    }

    @Test
    fun `cycle length rounds up`() {
        assertEquals(30, RevisionPlanner.cycleDays(30, 1))
        assertEquals(10, RevisionPlanner.cycleDays(30, 3))
        assertEquals(11, RevisionPlanner.cycleDays(31, 3))
        assertEquals(0, RevisionPlanner.cycleDays(0, 3))
    }

    // ── Dates ─────────────────────────────────────────────────────────────────

    @Test
    fun `iso weekday counts from monday`() {
        assertEquals(4, RevisionPlanner.isoWeekday(0))    // 1970-01-01, a Thursday
        assertEquals(1, RevisionPlanner.isoWeekday(4))    // the following Monday
        assertEquals(7, RevisionPlanner.isoWeekday(10))
        assertEquals(3, RevisionPlanner.isoWeekday(-1))   // Wednesday 1969-12-31
    }

    @Test
    fun `a log without a chosen date uses the local day of its timestamp`() {
        val late = RevisionLog(startPage = 1, endPage = 1, timestamp = 10 * day + 23 * 3_600_000L, manualStability = 1f)
        assertEquals(10L, RevisionPlanner.logDay(late, utc))
        assertEquals(11L, RevisionPlanner.logDay(late) { 2 * 3_600_000 }) // UTC+2 has already rolled over
    }

    @Test
    fun `last revised keeps the newest day per page`() {
        val last = RevisionPlanner.lastRevisedDays(listOf(log(1, 3, 5), log(2, 2, 9), log(2, 2, 7)), utc)
        assertEquals(5L, last[0])
        assertEquals(9L, last[1])
        assertEquals(5L, last[2])
        assertEquals(0L, last[3])
    }

    // ── Today ─────────────────────────────────────────────────────────────────

    private val items = RevisionPlanner.buildItems(PlanUnit.JUZ, covered(1..60), geometry)
    private val monday = 4L // epoch day 4 is a Monday

    @Test
    fun `never revised portions are due in mushaf order`() {
        val result = RevisionPlanner.todayPlan(plan(amount = 2), items, LongArray(604), noStates, monday)
        assertEquals(listOf(1, 2), result.due.map { it.number })
        assertTrue(result.done.isEmpty())
        assertEquals(2, result.cycleDays) // 3 portions at 2 a day
    }

    @Test
    fun `the portion revised longest ago comes first`() {
        val pageLast = RevisionPlanner.lastRevisedDays(listOf(log(1, 20, 1), log(21, 40, 3), log(41, 60, 2)), utc)
        val result = RevisionPlanner.todayPlan(plan(), items, pageLast, noStates, monday)
        assertEquals(listOf(1), result.due.map { it.number })
    }

    @Test
    fun `a portion revised today is done and uses up the amount`() {
        val pageLast = RevisionPlanner.lastRevisedDays(listOf(log(1, 20, monday)), utc)
        val result = RevisionPlanner.todayPlan(plan(amount = 2), items, pageLast, noStates, monday)
        assertEquals(listOf(1), result.done.map { it.number })
        assertEquals(listOf(2), result.due.map { it.number })
    }

    @Test
    fun `finishing the amount leaves nothing due`() {
        val pageLast = RevisionPlanner.lastRevisedDays(listOf(log(1, 20, monday)), utc)
        val result = RevisionPlanner.todayPlan(plan(amount = 1), items, pageLast, noStates, monday)
        assertTrue(result.due.isEmpty())
    }

    @Test
    fun `a partly revised portion is as fresh as its stalest page`() {
        val pageLast = RevisionPlanner.lastRevisedDays(listOf(log(1, 10, monday)), utc)
        val result = RevisionPlanner.todayPlan(plan(), items, pageLast, noStates, monday)
        assertTrue(result.done.isEmpty())
        // Juz 1 still has never-revised pages, so it is as stale as the rest and comes first in order.
        assertEquals(listOf(1), result.due.map { it.number })
    }

    @Test
    fun `rest days and pauses leave nothing due`() {
        val rest = RevisionPlanner.todayPlan(plan(rest = setOf(1)), items, LongArray(604), noStates, monday)
        assertTrue(rest.isRestDay)
        assertTrue(rest.due.isEmpty())
        val paused = RevisionPlanner.todayPlan(plan(paused = true), items, LongArray(604), noStates, monday)
        assertTrue(paused.isPaused)
        assertTrue(paused.due.isEmpty())
    }

    @Test
    fun `weakest first puts passages needing attention ahead of the rotation`() {
        val states = Array(604) { PassageState.NOT_ASSESSED }
        for (page in 41..60) states[page - 1] = PassageState.NEEDS_ATTENTION
        val pageLast = RevisionPlanner.lastRevisedDays(listOf(log(1, 20, 1), log(21, 40, 2), log(41, 60, 3)), utc)

        val rotation = RevisionPlanner.todayPlan(plan(order = PlanOrder.ROTATION), items, pageLast, states, monday)
        assertEquals(listOf(1), rotation.due.map { it.number })

        val weakest = RevisionPlanner.todayPlan(plan(order = PlanOrder.WEAKEST_FIRST), items, pageLast, states, monday)
        assertEquals(listOf(3), weakest.due.map { it.number })
    }

    // ── Strength-based plans ──────────────────────────────────────────────────

    private fun strengthPlan(strong: Int = 40, fair: Int = 20, weak: Int = 8, rest: Set<Int> = emptySet(), paused: Boolean = false) =
        RevisionPlan(name = "s", order = PlanOrder.BY_STRENGTH, strongDays = strong, fairDays = fair, weakDays = weak, restDays = rest, paused = paused)

    private fun statesOf(weak: IntRange? = null, strong: IntRange? = null): Array<PassageState> {
        val states = Array(604) { PassageState.NOT_ASSESSED }
        weak?.forEach { states[it - 1] = PassageState.NEEDS_ATTENTION }
        strong?.forEach { states[it - 1] = PassageState.RELIABLE }
        return states
    }

    @Test
    fun `pages fall into tiers by their passage state`() {
        val tiers = RevisionPlanner.tierPages(covered(1..60), statesOf(weak = 1..10, strong = 41..60))
        assertEquals((1..10).toList(), tiers.getValue(PlanTier.WEAK))
        assertEquals((11..40).toList(), tiers.getValue(PlanTier.FAIR))
        assertEquals((41..60).toList(), tiers.getValue(PlanTier.STRONG))
    }

    @Test
    fun `uncovered pages belong to no tier`() {
        assertTrue(RevisionPlanner.tierPages(null, statesOf()).values.all { it.isEmpty() })
        assertTrue(RevisionPlanner.tierPages(covered(1..5), statesOf(weak = 100..110)).getValue(PlanTier.WEAK).isEmpty())
    }

    @Test
    fun `each tier owes its pages spread over its cycle rounded up`() {
        assertEquals(10, RevisionPlanner.tierQuota(80, 8))
        assertEquals(3, RevisionPlanner.tierQuota(41, 20))
        assertEquals(0, RevisionPlanner.tierQuota(0, 8))
    }

    @Test
    fun `weak pages come around more often than strong ones`() {
        // 16 weak pages every 8 days = 2 a day; 40 strong pages every 40 days = 1 a day
        val today = strengthPlanToday(strengthPlan(), covered(1..16, 101..140), statesOf(weak = 1..16, strong = 101..140), LongArray(604))
        assertEquals(3, today.dailyPages)
        assertEquals(listOf(PlanTier.WEAK, PlanTier.STRONG), today.due.map { it.tier })
        assertEquals(listOf(1 to 2, 101 to 101), today.due.map { it.startPage to it.endPage })
    }

    private fun strengthPlanToday(plan: RevisionPlan, covered: BooleanArray, states: Array<PassageState>, last: LongArray) =
        RevisionPlanner.strengthToday(plan, covered, last, states, monday)

    @Test
    fun `the stalest pages of a tier are due first and neighbours join into one portion`() {
        val last = LongArray(604)
        for (p in 1..20) last[p - 1] = 1L            // revised long ago
        for (p in 21..40) last[p - 1] = monday - 1   // revised yesterday
        // 40 fair pages every 10 days = 4 a day
        val today = strengthPlanToday(strengthPlan(fair = 10), covered(1..40), statesOf(), last)
        assertEquals(listOf(1 to 4), today.due.map { it.startPage to it.endPage })
    }

    @Test
    fun `pages revised today use up the tier quota and show as done`() {
        val last = LongArray(604)
        last[0] = monday
        last[1] = monday
        val today = strengthPlanToday(strengthPlan(fair = 10), covered(1..40), statesOf(), last)
        assertEquals(listOf(1 to 2), today.done.map { it.startPage to it.endPage })
        assertEquals(listOf(3 to 4), today.due.map { it.startPage to it.endPage })
    }

    @Test
    fun `rest days and a paused plan ask for nothing`() {
        val wednesday = monday + 2
        val rest = RevisionPlanner.strengthToday(strengthPlan(rest = setOf(3)), covered(1..40), LongArray(604), statesOf(), wednesday)
        assertTrue(rest.isRestDay)
        assertTrue(rest.due.isEmpty())
        assertTrue(strengthPlanToday(strengthPlan(paused = true), covered(1..40), statesOf(), LongArray(604)).due.isEmpty())
    }

    @Test
    fun `a page that gets weaker moves to the weak tier by itself`() {
        val before = RevisionPlanner.tierPages(covered(1..5), statesOf(strong = 1..5))
        assertEquals(5, before.getValue(PlanTier.STRONG).size)
        val after = RevisionPlanner.tierPages(covered(1..5), statesOf(weak = 1..5))
        assertEquals(5, after.getValue(PlanTier.WEAK).size)
    }

    // ── Plan storage ──────────────────────────────────────────────────────────

    @Test
    fun `a plan round trips through its entity`() {
        val original = RevisionPlan(
            id = 3, name = "Daily", unit = PlanUnit.PAGE, amountPerDay = 5,
            restDays = setOf(5, 7), order = PlanOrder.WEAKEST_FIRST, paused = true, createdAt = 99L
        )
        assertEquals(original, RevisionPlan.fromEntity(original.toEntity()))
        assertEquals(0b1010000, original.toEntity().restDays)
    }

    @Test
    fun `tier cycles round trip through the entity`() {
        val original = RevisionPlan(name = "s", order = PlanOrder.BY_STRENGTH, strongDays = 60, fairDays = 25, weakDays = 5, createdAt = 1L)
        assertEquals(original, RevisionPlan.fromEntity(original.toEntity()))
    }
}
