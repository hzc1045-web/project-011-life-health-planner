package com.project011.lifehealthplanner.domain

import com.project011.lifehealthplanner.data.remote.BusyBlockDto
import com.project011.lifehealthplanner.data.remote.PlanDraftDto
import com.project011.lifehealthplanner.data.remote.PlanItemDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PlanValidatorTest {
    private val periodStart = Instant.parse("2026-09-01T00:00:00Z")
    private val periodEnd = Instant.parse("2026-09-08T00:00:00Z")

    @Test
    fun validPlanPasses() {
        val result = validate(draft(item()))
        assertTrue(result.errors.toString(), result.isValid)
    }

    @Test
    fun calendarAndPlanOverlapAreRejected() {
        val first = item(id = "one", start = "2026-09-02T08:00:00Z", end = "2026-09-02T09:00:00Z")
        val second = item(id = "two", start = "2026-09-02T08:30:00Z", end = "2026-09-02T09:30:00Z")
        val busy = listOf(BusyBlockDto("2026-09-02T08:45:00Z", "2026-09-02T10:00:00Z"))
        val result = validate(draft(first, second), busyBlocks = busy)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { "已有日历" in it })
        assertTrue(result.errors.any { "时间重叠" in it })
    }

    @Test
    fun weeklyBudgetIsEnforced() {
        val result = validate(draft(item(cost = 101.0)), weeklyBudget = 100.0)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { "预算" in it })
    }

    @Test
    fun urgentDraftIsBlocked() {
        val result = validate(draft(item(), riskLevel = "urgent"))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { "紧急风险" in it })
    }

    @Test
    fun schemaRangesAreEnforced() {
        val invalid = item(priority = 7, energy = "extreme", cost = -1.0, reminders = listOf(2_000))
        val result = validate(draft(invalid))
        assertFalse(result.isValid)
        assertTrue(result.errors.size >= 4)
    }

    @Test
    fun highIntensityTagRespectsHealthConstraint() {
        val result = validate(
            draft(item(safetyTags = listOf("high_intensity"))),
            healthConstraints = listOf("医生要求避免剧烈运动"),
        )
        assertFalse(result.isValid)
        assertTrue(result.errors.any { "高强度" in it })
    }

    private fun validate(
        draft: PlanDraftDto,
        busyBlocks: List<BusyBlockDto> = emptyList(),
        weeklyBudget: Double? = null,
        healthConstraints: List<String> = emptyList(),
    ) = PlanValidator.validate(
        draft,
        periodStart,
        periodEnd,
        busyBlocks,
        weeklyBudget,
        healthConstraints,
    )

    private fun draft(vararg items: PlanItemDto, riskLevel: String = "normal") = PlanDraftDto(
        title = "测试计划",
        summary = "测试",
        rationale = emptyList(),
        riskLevel = riskLevel,
        riskMessage = "",
        items = items.toList(),
        reviewQuestions = emptyList(),
    )

    private fun item(
        id: String = "item-1",
        start: String = "2026-09-02T08:00:00Z",
        end: String = "2026-09-02T09:00:00Z",
        priority: Int = 3,
        energy: String = "medium",
        cost: Double = 0.0,
        reminders: List<Int> = listOf(15),
        safetyTags: List<String> = emptyList(),
    ) = PlanItemDto(
        id = id,
        domain = "health",
        title = id,
        description = "",
        startAt = start,
        endAt = end,
        priority = priority,
        energy = energy,
        estimatedCost = cost,
        goalIds = emptyList(),
        reminderMinutes = reminders,
        safetyTags = safetyTags,
    )
}
