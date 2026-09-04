package com.project011.lifehealthplanner.domain

import java.time.Instant
import java.time.temporal.ChronoUnit

internal data class PlanPeriod(
    val start: Instant,
    val end: Instant,
) {
    init {
        require(end.isAfter(start)) { "计划周期无效" }
    }

    companion object {
        fun forDays(anchor: Instant, requestedDays: Int): PlanPeriod {
            val start = Instant.ofEpochMilli(anchor.toEpochMilli())
            val end = start.plus(requestedDays.coerceIn(1, 31).toLong(), ChronoUnit.DAYS)
            return PlanPeriod(start, end)
        }

        fun fromEpochMillis(start: Long, end: Long): PlanPeriod = PlanPeriod(
            start = Instant.ofEpochMilli(start),
            end = Instant.ofEpochMilli(end),
        )
    }
}
