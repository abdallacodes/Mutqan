package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanAdviceTest {

    private val monday = 1000L + Math.floorMod(4L - 1000L, 7L)
    private val today = monday + 28
    // Started 28 days ago at 7 pages a day; the whole hifz in a week would be 29 a day.
    private val memory = AdviceMemory(amountSince = monday)
    private val weekly = 29

    /** Pages revised on each of the days before today: [pages] on the days in [kept], none on the others. */
    private fun days(kept: Set<Int>, pages: Int = 7): (Long) -> Int = { day -> if ((today - day).toInt() in kept) pages else 0 }

    private fun advise(
        pagesOn: (Long) -> Int,
        amount: Int = 7,
        memory: AdviceMemory = this.memory,
        weakNow: Int = 10,
        weakBefore: Int = 10,
        rest: Set<Int> = emptySet(),
        paused: Boolean = false
    ) = PlanAdvisor.advise(amount, rest, paused, today, memory, pagesOn, weakNow, weakBefore, weekly)

    @Test
    fun `kept up 12 of the last 14 days suggests the next step up`() {
        val advice = advise(days(kept = (1..14).toSet() - setOf(3, 9)))!!
        assertEquals(PlanAdvice.Kind.MORE, advice.kind)
        assertEquals(10, advice.to)
        assertEquals(12, advice.keptUp)
        assertEquals(14, advice.of)
    }

    @Test
    fun `not when the weak pages are increasing, nor with only 11 days kept`() {
        assertNull(advise(days(kept = (1..14).toSet()), weakNow = 12, weakBefore = 10))
        assertNull(advise(days(kept = (1..14).toSet() - setOf(1, 2, 3))))
    }

    @Test
    fun `revising well beyond the plan suggests the step that covers it`() {
        val advice = advise(days(kept = (1..14).toSet(), pages = 12))!!
        assertEquals(15, advice.to)
        assertEquals(12, advice.beyond)
    }

    @Test
    fun `finishing fewer than half of the days suggests the step below`() {
        val advice = advise(days(kept = setOf(1, 2, 5, 8, 11, 13)))!!
        assertEquals(PlanAdvice.Kind.LESS, advice.kind)
        assertEquals(5, advice.to)
        // Half kept is not too few.
        assertNull(advise(days(kept = setOf(1, 2, 3, 4, 5, 6, 7))))
    }

    @Test
    fun `quiet in an amount's first two weeks, while paused, and for two weeks after an answer`() {
        val allKept = days(kept = (1..30).toSet())
        assertNull(advise(allKept, memory = AdviceMemory(amountSince = today - 10)))
        assertNull(advise(allKept, paused = true))
        assertNull(advise(allKept, memory = memory.copy(lastAnswered = today - 13)))
        assertEquals(PlanAdvice.Kind.MORE, advise(allKept, memory = memory.copy(lastAnswered = today - 14))!!.kind)
    }

    @Test
    fun `rest days are not counted as missed`() {
        // Every day kept but Fridays, which are rest days.
        val notFriday: (Long) -> Int = { day -> if (RevisionPlanner.isoWeekday(day) == 5) 0 else 7 }
        assertEquals(PlanAdvice.Kind.MORE, advise(notFriday, rest = setOf(5))!!.kind)
    }

    @Test
    fun `no step past every page every week`() {
        assertNull(advise(days(kept = (1..14).toSet(), pages = 29), amount = 29))
    }

    @Test
    fun `a higher amount missed on most of its first week offers to go back`() {
        val tried = AdviceMemory(amountSince = today - 7, lastAnswered = today - 7, triedFrom = 7, triedOn = today - 7)
        val advice = advise(days(kept = setOf(2, 5), pages = 10), amount = 10, memory = tried)!!
        assertEquals(PlanAdvice.Kind.BACK, advice.kind)
        assertEquals(7, advice.to)
        // Kept up on most of them: no.
        assertNull(advise(days(kept = setOf(1, 2, 3, 4, 6), pages = 10), amount = 10, memory = tried))
    }

    @Test
    fun `pages revised a day count each memorised page once`() {
        val day = 86_400_000L
        val logs = listOf(
            RevisionLog(startPage = 1, endPage = 5, timestamp = monday * day, manualStability = 1f, dateMillis = monday * day),
            RevisionLog(startPage = 4, endPage = 8, timestamp = monday * day, manualStability = 1f, dateMillis = monday * day)
        )
        val covered = BooleanArray(604).also { for (p in 1..6) it[p - 1] = true }
        assertEquals(mapOf(monday to 6), PlanAdvisor.pagesByDay(logs, covered, monday - 30) { 0 })
    }
}
