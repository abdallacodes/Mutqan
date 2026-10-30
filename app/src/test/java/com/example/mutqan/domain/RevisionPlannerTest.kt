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
}
