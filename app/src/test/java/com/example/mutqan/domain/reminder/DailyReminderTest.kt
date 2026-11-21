package com.example.mutqan.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyReminderTest {

    private val zone = ZoneId.of("Asia/Jerusalem")

    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        ZonedDateTime.of(2026, 11, day, hour, minute, second, 0, zone).toInstant().toEpochMilli()

    @Test
    fun laterTodayWhenTheTimeIsStillAhead() {
        assertEquals(at(21, 20, 0), DailyReminder.nextTime(at(21, 19, 0), 20 * 60, zone))
    }

    @Test
    fun tomorrowOnceTheTimeHasPassed() {
        assertEquals(at(22, 20, 0), DailyReminder.nextTime(at(21, 21, 30), 20 * 60, zone))
    }

    @Test
    fun aFiringJustAfterItsTimeSetsTomorrow() {
        assertEquals(at(22, 20, 0), DailyReminder.nextTime(at(21, 20, 0, 5), 20 * 60, zone))
    }

    @Test
    fun earlyMorningTimes() {
        assertEquals(at(22, 5, 15), DailyReminder.nextTime(at(21, 23, 0), 5 * 60 + 15, zone))
    }
}
