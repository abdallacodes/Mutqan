package com.example.mutqan.domain.time

import com.example.mutqan.data.local.dao.TimeTotal
import com.example.mutqan.data.local.entity.TimeSpentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSummaryTest {

    @Test
    fun todayTheWeekAndTheTotals() {
        val rows = listOf(
            TimeSpentEntity(100, "mushaf", 60_000),
            TimeSpentEntity(100, "practice", 30_000),
            TimeSpentEntity(95, "mushaf", 120_000),
            TimeSpentEntity(90, "mushaf", 999_000)
        )
        val totals = listOf(TimeTotal("mushaf", 1_179_000), TimeTotal("practice", 30_000))
        val s = TimeSummary.of(rows, totals, today = 100)
        assertEquals(60_000, s.todayMushaf)
        assertEquals(30_000, s.todayPractice)
        assertEquals(7, s.week.size)
        assertEquals(94L, s.week.first().first)
        assertEquals(100L, s.week.last().first)
        // Day 90 is outside the last seven days.
        assertEquals(180_000, s.weekMushaf)
        assertEquals(30_000, s.weekPractice)
        assertEquals(1_179_000, s.totalMushaf)
    }

    @Test
    fun nothingCountedYet() {
        assertTrue(TimeSummary.of(emptyList(), emptyList(), today = 5).isEmpty)
    }
}
