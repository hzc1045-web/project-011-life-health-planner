package com.project011.lifehealthplanner.domain

import com.project011.lifehealthplanner.data.remote.PlanDraftDto
import com.project011.lifehealthplanner.data.remote.PlanItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class PlanPeriodTest {
    @Test
    fun dailyDraftKeepsItsPeriodWhenConfirmed() = assertConfirmationPeriod(1)

    @Test
    fun weeklyDraftKeepsItsPeriodWhenConfirmed() = assertConfirmationPeriod(7)

    @Test
    fun monthlyDraftKeepsItsPeriodWhenConfirmed() = assertConfirmationPeriod(30)

    private fun assertConfirmationPeriod(days: Int) {
        val generated = PlanPeriod.forDays(Instant.parse("2026-09-04T01:02:03.123456789Z"), days)
        val restored = PlanPeriod.fromEpochMillis(generated.start.toEpochMilli(), generated.end.toEpochMilli())

        assertEquals(generated, restored)
        assertEquals(days.toLong(), Duration.between(restored.start, restored.end).toDays())
        val validation = PlanValidator.validate(
            draft = draftAtPeriodBoundary(restored),
            periodStart = restored.start,
            periodEnd = restored.end,
            busyBlocks = emptyList(),
            weeklyBudget = null,
        )
        assertTrue(validation.errors.toString(), validation.isValid)
    }

    private fun draftAtPeriodBoundary(period: PlanPeriod) = PlanDraftDto(
        title = "周期测试",
        summary = "",
        rationale = emptyList(),
        riskLevel = "normal",
        riskMessage = "",
        items = listOf(
            PlanItemDto(
                id = "boundary-item",
                domain = "health",
                title = "边界事项",
                description = "",
                startAt = period.start.toString(),
                endAt = period.end.toString(),
                priority = 3,
                energy = "medium",
                estimatedCost = 0.0,
                goalIds = emptyList(),
                reminderMinutes = listOf(15),
                safetyTags = emptyList(),
            ),
        ),
        reviewQuestions = emptyList(),
    )
}
