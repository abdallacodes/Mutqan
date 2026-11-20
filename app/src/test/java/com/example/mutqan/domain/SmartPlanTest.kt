package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPlanTest {

    private val monday = 1000L + Math.floorMod(4L - 1000L, 7L) // an epoch day that is a Monday

    private fun covered(vararg pages: IntRange): BooleanArray =
        BooleanArray(604).also { arr -> pages.forEach { r -> r.forEach { arr[it - 1] = true } } }

    private fun statesOf(weak: Iterable<Int> = emptyList(), strong: Iterable<Int> = emptyList()): Array<PassageState> {
        val states = Array(604) { PassageState.NOT_ASSESSED }
        weak.forEach { states[it - 1] = PassageState.NEEDS_ATTENTION }
        strong.forEach { states[it - 1] = PassageState.RELIABLE }
        return states
    }

    private fun plan(pages: Int, rest: Set<Int> = emptySet(), paused: Boolean = false) =
        RevisionPlan(name = "s", unit = PlanUnit.PAGE, amountPerDay = pages, order = PlanOrder.BY_STRENGTH, restDays = rest, paused = paused)

    private fun pagesOf(today: TodayPlan) = today.due.flatMap { it.startPage..it.endPage }

    // ── The cycle ─────────────────────────────────────────────────────────────

    @Test
    fun `more pages a day make a shorter cycle, weak pages coming three times as often as strong ones`() {
        // 200 pages, 190 strong and 10 fair, at 20 a day: the whole hifz every 10.5 days, the fair pages every 5.
        val weighted = SmartPlan.weighted(mapOf(PlanTier.STRONG to 190, PlanTier.FAIR to 10))
        val cycle = SmartPlan.cycleDays(weighted, 20, emptySet())
        assertEquals(10.5f, cycle, 0.01f)
        assertEquals(5.25f, SmartPlan.intervalDays(PlanTier.FAIR, cycle), 0.01f)
        // The user's case: 100 weak, 1 fair, 2 strong at 6 a day. Weak pages every 17 days, strong ones every 51: not the
        // other way round, as with 3 + 2 + 1 a day for the three groups (strong every 2 days, weak every 34).
        val case = SmartPlan.weighted(mapOf(PlanTier.WEAK to 100, PlanTier.FAIR to 1, PlanTier.STRONG to 2))
        val caseCycle = SmartPlan.cycleDays(case, 6, emptySet())
        assertEquals(16.9f, SmartPlan.intervalDays(PlanTier.WEAK, caseCycle), 0.1f)
        assertEquals(50.7f, SmartPlan.intervalDays(PlanTier.STRONG, caseCycle), 0.1f)
        // Rest days stretch the cycle.
        assertEquals(10.5f * 7f / 6f, SmartPlan.cycleDays(weighted, 20, setOf(5)), 0.01f)
    }

    @Test
    fun `the steps end at every page every week, and a good start is modest`() {
        // The whole Quran, all strong: 87 pages a day for every page every week (101 with one rest day).
        assertEquals(87, SmartPlan.weeklyPages(604, emptySet()))
        assertEquals(101, SmartPlan.weeklyPages(604, setOf(5)))
        assertEquals(listOf(2, 3, 5, 7, 10, 15, 20, 30, 40, 60, 80, 87), SmartPlan.steps(87))
        assertEquals(listOf(2, 3, 5, 7, 10, 15, 20, 29), SmartPlan.steps(29))
        assertEquals(10, SmartPlan.goodStart(604, 87))
        assertEquals(7, SmartPlan.goodStart(200, 29))
        assertEquals(3, SmartPlan.goodStart(50, 8))
        // Never more than every page every week.
        assertEquals(2, SmartPlan.goodStart(10, 2))
    }

    // ── Today ─────────────────────────────────────────────────────────────────

    @Test
    fun `a new plan starts in Mushaf order, one stretch of the day's pages`() {
        val today = SmartPlan.today(plan(6), covered(1..40), LongArray(604), statesOf(), monday)
        assertEquals(listOf(1 to 6), today.due.map { it.startPage to it.endPage })
        assertEquals(6, today.dailyPages)
    }

    @Test
    fun `the pages revised longest ago come first`() {
        val last = LongArray(604)
        for (p in 1..10) last[p - 1] = monday - 1
        for (p in 11..20) last[p - 1] = monday - 30
        val today = SmartPlan.today(plan(6), covered(1..20), last, statesOf(), monday)
        assertEquals((11..16).toList(), pagesOf(today))
    }

    @Test
    fun `weak pages come before others revised as long ago`() {
        val last = LongArray(604) { monday - 10 }
        val today = SmartPlan.today(plan(6), covered(1..100), last, statesOf(weak = 50..52), monday)
        assertTrue((50..52).all { it in pagesOf(today) })
        assertEquals(6, pagesOf(today).size)
        assertTrue(today.due.size <= 2)
    }

    @Test
    fun `the user's case starts with weak pages, not the strong ones`() {
        val last = LongArray(604) { monday - 20 }
        val today = SmartPlan.today(plan(6), covered(1..103), last, statesOf(weak = 1..100, strong = 102..103), monday)
        assertTrue(pagesOf(today).all { it in 1..100 })
    }

    @Test
    fun `a day is at most two stretches, more only when two cannot hold it`() {
        val last = LongArray(604) { monday - 1 }
        last[4] = 0L   // page 5, weak, never revised
        last[24] = 0L  // page 25, weak, never revised
        val two = SmartPlan.today(plan(4), covered(1..10, 20..30), last, statesOf(weak = listOf(5, 25)), monday)
        assertTrue(two.due.size <= 2)
        assertTrue(5 in pagesOf(two) && 25 in pagesOf(two))
        assertEquals(4, pagesOf(two).size)
        // Pages memorised one here and one there: two stretches cannot hold four pages, so there are four.
        val scattered = BooleanArray(604).also { for (p in listOf(1, 3, 5, 7, 9)) it[p - 1] = true }
        val apart = SmartPlan.today(plan(4), scattered, LongArray(604), statesOf(), monday)
        assertEquals(4, apart.due.size)
    }

    @Test
    fun `pages revised today count toward the day and are not asked again`() {
        val last = LongArray(604)
        last[0] = monday
        last[1] = monday
        val today = SmartPlan.today(plan(6), covered(1..40), last, statesOf(), monday)
        assertEquals(listOf(1 to 2), today.done.map { it.startPage to it.endPage })
        assertEquals(4, pagesOf(today).size)
        assertTrue(1 !in pagesOf(today) && 2 !in pagesOf(today))
    }

    @Test
    fun `rest days and a paused plan ask for nothing`() {
        val wednesday = monday + 2
        val rest = SmartPlan.today(plan(6, rest = setOf(3)), covered(1..40), LongArray(604), statesOf(), wednesday)
        assertTrue(rest.isRestDay)
        assertTrue(rest.due.isEmpty())
        assertTrue(SmartPlan.today(plan(6, paused = true), covered(1..40), LongArray(604), statesOf(), monday).due.isEmpty())
    }

    @Test
    fun `each stretch says what it holds and how long ago it was revised`() {
        val last = LongArray(604) { monday - 9 }
        val today = SmartPlan.today(plan(3), covered(1..3), last, statesOf(weak = listOf(2)), monday)
        val why = today.due.single().why!!
        assertEquals(1, why.weak)
        assertEquals(2, why.fair)
        assertEquals(9, why.daysAgo)
        val fresh = SmartPlan.today(plan(3), covered(1..3), LongArray(604), statesOf(), monday)
        assertEquals(null, fresh.due.single().why!!.daysAgo)
    }

    // ── Over time ─────────────────────────────────────────────────────────────

    @Test
    fun `over months each strength comes around as often as the cycle says`() {
        // The user's case, revised every day as the plan asks.
        val weak = 1..100
        val strong = 102..103
        val states = statesOf(weak = weak, strong = strong)
        val cover = covered(1..103)
        val last = LongArray(604)
        val revisions = HashMap<Int, MutableList<Long>>()
        for (d in 0 until 400) {
            val day = monday + d
            val today = SmartPlan.today(plan(6), cover, last, states, day)
            for (page in pagesOf(today)) {
                last[page - 1] = day
                if (d >= 200) revisions.getOrPut(page) { ArrayList() } += day
            }
        }
        fun meanGap(pages: Iterable<Int>): Float {
            val gaps = pages.flatMap { p -> revisions[p].orEmpty().zipWithNext { a, b -> (b - a).toFloat() } }
            return gaps.average().toFloat()
        }
        // Weak pages about every 17 days, strong ones about every 51.
        assertEquals(16.9f, meanGap(weak), 2f)
        assertEquals(50.7f, meanGap(strong), 8f)
    }
}
