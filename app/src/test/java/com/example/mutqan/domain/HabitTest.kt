package com.example.mutqan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitTest {

    private val noRest: (Long) -> Boolean = { false }
    private val today = 1000L

    private fun days(vararg back: Int): Set<Long> = back.map { today - it }.toSet()

    @Test
    fun aDayCountsWithARevisionOrTenAnswers() {
        val kept = Habit.keptDays(listOf(5L), List(9) { 6L } + List(10) { 7L })
        assertEquals(setOf(5L, 7L), kept)
    }

    @Test
    fun todayStillOpenDoesNotBreakTheRun() {
        val streak = Habit.streak(days(1, 2, 3), noRest, today)
        assertEquals(3, streak.days)
        assertEquals(today - 3, streak.since)
        assertFalse(streak.todayKept)
    }

    @Test
    fun todayKeptCounts() {
        val streak = Habit.streak(days(0, 1, 2), noRest, today)
        assertEquals(3, streak.days)
        assertTrue(streak.todayKept)
    }

    @Test
    fun oneMissedDayInAWeekIsForgiven() {
        // Kept 0..3 and 5..9 back, missed 4 back: one run of nine days.
        val streak = Habit.streak(days(0, 1, 2, 3, 5, 6, 7, 8, 9), noRest, today)
        assertEquals(9, streak.days)
        assertEquals(today - 9, streak.since)
    }

    @Test
    fun twoMissedDaysInAWeekEndTheRun() {
        // Missed 2 and 5 back: the second, within a week of the first, ends the run.
        val streak = Habit.streak(days(0, 1, 3, 4, 6, 7), noRest, today)
        assertEquals(4, streak.days)
        assertEquals(today - 4, streak.since)
    }

    @Test
    fun missedDaysAWeekApartAreBothForgiven() {
        // Missed 2 and 9 back: seven days apart.
        val kept = days(0, 1, 3, 4, 5, 6, 7, 8, 10, 11)
        assertEquals(10, Habit.streak(kept, noRest, today).days)
    }

    @Test
    fun twoMissedDaysInARowEndTheRun() {
        val streak = Habit.streak(days(1, 2, 5, 6), noRest, today)
        assertEquals(2, streak.days)
    }

    @Test
    fun restDaysNeitherAddNorBreak() {
        val rest: (Long) -> Boolean = { it == today - 2 || it == today - 5 }
        val streak = Habit.streak(days(1, 3, 4, 6), rest, today)
        assertEquals(4, streak.days)
    }

    @Test
    fun yesterdayMissedAndTodayOpenStillHoldsTheRun() {
        val streak = Habit.streak(days(2, 3, 4), noRest, today)
        assertEquals(3, streak.days)
    }

    @Test
    fun noActivityNoRun() {
        val streak = Habit.streak(emptySet(), noRest, today)
        assertEquals(0, streak.days)
        assertNull(streak.since)
    }

    @Test
    fun theLongestRunIsKeptAfterABreak() {
        // A run of 5 (10..14 back), a break (8 and 9 back missed), then a run of 3 (0..2 back) with 3..7 missed.
        val kept = days(0, 1, 2, 10, 11, 12, 13, 14)
        assertEquals(5, Habit.best(kept, noRest, today))
        assertEquals(3, Habit.streak(kept, noRest, today).days)
        assertEquals(0, Habit.best(emptySet(), noRest, today))
    }

    @Test
    fun daysWithNothingAroundThemAreMissedNotForgiven() {
        val week = Habit.week(days(9), noRest, today).map { it.second }
        assertEquals(List(6) { DayMark.MISSED } + DayMark.OPEN, week)
    }

    @Test
    fun aRestDayTodayHoldsTheRunLikeAnOpenDay() {
        val rest: (Long) -> Boolean = { it == today }
        val marks = Habit.week(days(2, 3), rest, today).map { it.second }
        // Yesterday missed, bridged by today's rest day (still open) and the day before, kept.
        assertEquals(DayMark.REST, marks.last())
        assertEquals(DayMark.FORGIVEN, marks[5])
        assertEquals(2, Habit.streak(days(2, 3), rest, today).days)
    }

    @Test
    fun theWeekStripMarksEachDay() {
        val rest: (Long) -> Boolean = { it == today - 3 }
        val week = Habit.week(days(1, 2, 5, 6), rest, today).map { it.second }
        // Oldest first: 6 kept, 5 kept, 4 forgiven, 3 rest, 2 kept, 1 kept, today open.
        assertEquals(
            listOf(DayMark.KEPT, DayMark.KEPT, DayMark.FORGIVEN, DayMark.REST, DayMark.KEPT, DayMark.KEPT, DayMark.OPEN),
            week
        )
    }

    @Test
    fun theWardIsThePortionsAndTheQuestions() {
        val ward = Ward(portionsDone = 2, portions = 3, answered = 10)
        assertTrue(ward.questionsDone)
        assertFalse(ward.revisionDone)
        assertFalse(ward.complete)
        assertEquals(2f / 3, ward.revisionShare, 0.001f)
        assertTrue(Ward(0, 0, 12).complete)
        assertEquals(7, Ward(0, 0, 3).questionsLeft)
    }

    @Test
    fun aPassEndsWhenTheLastMemorisedPageIsRevised() {
        val covered = BooleanArray(10) { it < 4 } // pages 1..4
        fun log(start: Int, end: Int, day: Long) = RevisionLog(startPage = start, endPage = end, timestamp = day, manualStability = 1f)
        val logs = listOf(log(1, 2, 1), log(3, 3, 2), log(9, 9, 3), log(4, 4, 5), log(1, 4, 8), log(2, 2, 9))
        val passes = RevisionPasses.completed(logs, covered) { it.timestamp }
        assertEquals(listOf(RevisionPasses.Pass(1, 1, 5), RevisionPasses.Pass(2, 8, 8)), passes)
        assertEquals(5, passes[0].days)
    }

    @Test
    fun aRunReachingFurtherBackDoesNotBringItsMomentAgain() {
        val seen = setOf(Milestone.Days(3, 19).key)
        // A forgotten day logged before the run: the same run now starts on day 18.
        assertNull(Milestones.next(listOf(Milestone.Days(3, 18)), seen))
        // Its next threshold still comes.
        assertEquals(Milestone.Days(7, 18), Milestones.next(listOf(Milestone.Days(3, 18), Milestone.Days(7, 18)), seen))
        // A new run after a break gets its own moment.
        assertEquals(Milestone.Days(3, 30), Milestones.next(listOf(Milestone.Days(3, 30)), seen))
    }

    @Test
    fun milestonesComeOnceAndTheBiggestRunFirst() {
        val streak = Streak(days = 8, since = 900, todayKept = true)
        val reached = Milestones.reached(streak, listOf(RevisionPasses.Pass(1, 1, 20)), listOf(30))
        val first = Milestones.next(reached, emptySet())
        assertEquals(Milestone.Days(7, 900), first)
        val seen = Milestones.keysShown(first!!).toSet()
        assertEquals(setOf("days:900:3", "days:900:7"), seen)
        assertEquals(Milestone.Pass(1, 20), Milestones.next(reached, seen))
        assertEquals(Milestone.StrongJuz(30), Milestones.next(reached, seen + "pass:1"))
        assertNull(Milestones.next(reached, seen + "pass:1" + "juz:30"))
    }

    @Test
    fun aJuzIsStrongWhenAllItsPagesAreMemorisedAndStrong() {
        val covered = BooleanArray(10) { it != 9 }
        val juz = mapOf(1 to listOf(1, 2, 3), 2 to listOf(4, 5), 3 to listOf(9, 10))
        assertEquals(setOf(1), Milestones.strongJuz(juz, covered, listOf(1, 2, 3, 4, 9)))
    }
}
