package com.project011.lifehealthplanner.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

internal data class OfflineScheduleSlot(
    val itemIndex: Int,
    val start: Instant,
    val end: Instant,
)

internal object OfflinePlanSchedule {
    private val preferredTimes = listOf(LocalTime.of(7, 30), LocalTime.of(18, 30))

    fun create(
        periodStart: Instant,
        periodEnd: Instant,
        durationsMinutes: List<Long>,
        zoneId: ZoneId,
    ): List<OfflineScheduleSlot> {
        require(periodEnd.isAfter(periodStart)) { "计划周期无效" }
        val slots = mutableListOf<OfflineScheduleSlot>()
        var cursor = periodStart
        durationsMinutes.forEachIndexed { index, durationMinutes ->
            if (durationMinutes <= 0) return@forEachIndexed
            val candidateStart = nextPreferredTime(cursor, zoneId)
            val candidateEnd = candidateStart.plusSeconds(durationMinutes * 60)
            if (!candidateEnd.isAfter(periodEnd)) {
                slots += OfflineScheduleSlot(index, candidateStart, candidateEnd)
                cursor = candidateEnd
            }
        }
        if (slots.isNotEmpty()) return slots

        durationsMinutes.forEachIndexed { index, durationMinutes ->
            if (durationMinutes > 0) {
                val fallbackEnd = periodStart.plusSeconds(durationMinutes * 60)
                if (!fallbackEnd.isAfter(periodEnd)) {
                    return listOf(OfflineScheduleSlot(index, periodStart, fallbackEnd))
                }
            }
        }
        return emptyList()
    }

    private fun nextPreferredTime(cursor: Instant, zoneId: ZoneId): Instant {
        val localCursor = cursor.atZone(zoneId)
        repeat(3) { dayOffset ->
            val date = localCursor.toLocalDate().plusDays(dayOffset.toLong())
            preferredTimes.forEach { time ->
                val candidate = date.atTime(time).atZone(zoneId).toInstant()
                if (!candidate.isBefore(cursor)) return candidate
            }
        }
        return cursor
    }
}
