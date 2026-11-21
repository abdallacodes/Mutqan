package com.example.mutqan.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

class DailyReminderTest {

    private val zoneId = ZoneId.of("Asia/Jerusalem")
    private val zone = TimeZone.getTimeZone(zoneId)

    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0, month: Int = 11): Long =
        ZonedDateTime.of(2026, month, day, hour, minute, second, 0, zoneId).toInstant().toEpochMilli()

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
    fun theTimeOfDayStaysAcrossAClockChange() {
        // Summer time ends in Israel on the night of 24-25 October 2026: the day after is 25 hours long.
        assertEquals(at(25, 20, 0, month = 10), DailyReminder.nextTime(at(24, 21, 0, month = 10), 20 * 60, zone))
    }

    @Test
    fun earlyMorningTimes() {
        assertEquals(at(22, 5, 15), DailyReminder.nextTime(at(21, 23, 0), 5 * 60 + 15, zone))
    }
}
