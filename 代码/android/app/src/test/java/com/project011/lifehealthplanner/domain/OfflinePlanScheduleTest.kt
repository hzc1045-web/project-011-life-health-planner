package com.project011.lifehealthplanner.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class OfflinePlanScheduleTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val durations = listOf(30L, 45L, 45L, 30L)

    @Test
    fun dailyScheduleIsNotEmptyNearMidnight() {
        val start = ZonedDateTime.of(2026, 9, 4, 0, 1, 0, 0, zone).toInstant()
        val slots = assertScheduleWithinPeriod(start, 1)

        assertFalse(slots.isEmpty())
        assertTrue(slots.first().start.atZone(zone).toLocalDate() == start.atZone(zone).toLocalDate())
    }

    @Test
    fun dailyScheduleStaysValidForEveryMinuteOfTheDay() {
        val dayStart = ZonedDateTime.of(2026, 9, 4, 0, 0, 0, 0, zone).toInstant()
        repeat(24 * 60) { minute ->
            assertScheduleWithinPeriod(dayStart.plus(minute.toLong(), ChronoUnit.MINUTES), 1)
        }
    }

    @Test
    fun weeklyScheduleIsNotEmptyAndWithinPeriod() {
        val start = ZonedDateTime.of(2026, 9, 4, 7, 31, 0, 0, zone).toInstant()
        assertScheduleWithinPeriod(start, 7)
    }

    @Test
    fun monthlyScheduleIsNotEmptyAndWithinPeriod() {
        val start = ZonedDateTime.of(2026, 9, 4, 23, 59, 0, 0, zone).toInstant()
        assertScheduleWithinPeriod(start, 30)
    }

    private fun assertScheduleWithinPeriod(start: Instant, days: Long): List<OfflineScheduleSlot> {
        val end = start.plus(days, ChronoUnit.DAYS)
        val slots = OfflinePlanSchedule.create(start, end, durations, zone)

        assertFalse("$days 天周期不应为空", slots.isEmpty())
        slots.forEach { slot ->
            assertFalse(slot.start.isBefore(start))
            assertFalse(slot.end.isAfter(end))
            assertTrue(slot.end.isAfter(slot.start))
            assertTrue(slot.itemIndex in durations.indices)
        }
        return slots
    }
}
