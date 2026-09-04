package com.project011.lifehealthplanner.domain

import com.project011.lifehealthplanner.data.remote.BusyBlockDto
import com.project011.lifehealthplanner.data.remote.PlanDraftDto
import java.time.Instant
import java.time.Duration

data class PlanValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
)

object PlanValidator {
    fun validate(
        draft: PlanDraftDto,
        periodStart: Instant,
        periodEnd: Instant,
        busyBlocks: List<BusyBlockDto>,
        weeklyBudget: Double?,
        healthConstraints: List<String> = emptyList(),
    ): PlanValidationResult {
        val errors = mutableListOf<String>()
        if (draft.riskLevel == "urgent") errors += "存在紧急风险，普通计划不能采用"
        if (draft.riskLevel !in setOf("normal", "caution", "urgent")) errors += "计划风险等级无效"
        val periodDays = Duration.between(periodStart, periodEnd).toMinutes() / (24.0 * 60.0)
        val itemLimit = when {
            periodDays <= 1.0 -> 6
            periodDays <= 7.0 -> 14
            else -> 30
        }
        if (draft.items.size > itemLimit) errors += "计划事项超过本周期上限 $itemLimit 项"
        val parsedItems = draft.items.mapNotNull { item ->
            runCatching {
                Triple(item, Instant.parse(item.startAt), Instant.parse(item.endAt))
            }.getOrElse {
                errors += "${item.title} 的时间格式无效"
                null
            }
        }
        parsedItems.forEach { (item, start, end) ->
            if (item.domain !in VALID_DOMAINS) errors += "${item.title} 的领域无效"
            if (item.priority !in 1..5) errors += "${item.title} 的优先级必须在 1 到 5 之间"
            if (item.energy !in VALID_ENERGY) errors += "${item.title} 的精力要求无效"
            if (item.estimatedCost < 0) errors += "${item.title} 的预计费用不能为负数"
            if (item.reminderMinutes.any { it !in 0..1_440 }) errors += "${item.title} 的提醒时间无效"
            if (!end.isAfter(start)) errors += "${item.title} 的结束时间必须晚于开始时间"
            if (start.isBefore(periodStart) || end.isAfter(periodEnd)) {
                errors += "${item.title} 超出计划周期"
            }
            busyBlocks.forEach { block ->
                val busyStart = runCatching { Instant.parse(block.startAt) }.getOrNull()
                val busyEnd = runCatching { Instant.parse(block.endAt) }.getOrNull()
                if (busyStart != null && busyEnd != null && overlaps(start, end, busyStart, busyEnd)) {
                    errors += "${item.title} 与已有日历忙碌时段冲突"
                }
            }
            healthConstraintError(item.safetyTags, healthConstraints)?.let {
                errors += "${item.title}：$it"
            }
        }
        parsedItems.sortedBy { it.second }.zipWithNext().forEach { (first, second) ->
            if (overlaps(first.second, first.third, second.second, second.third)) {
                errors += "${first.first.title} 与 ${second.first.title} 时间重叠"
            }
        }
        val periodBudget = weeklyBudget?.times(maxOf(1.0, periodDays / 7.0))
        if (periodBudget != null && draft.items.sumOf { it.estimatedCost } > periodBudget) {
            errors += "计划预计费用超过本周期可用预算"
        }
        if (draft.items.map { it.id }.distinct().size != draft.items.size) {
            errors += "计划项目 ID 重复"
        }
        return PlanValidationResult(errors.isEmpty(), errors.distinct())
    }

    private fun overlaps(aStart: Instant, aEnd: Instant, bStart: Instant, bEnd: Instant): Boolean =
        aStart < bEnd && bStart < aEnd

    private fun healthConstraintError(tags: List<String>, constraints: List<String>): String? {
        if (constraints.isEmpty()) return null
        val normalized = constraints.joinToString(" ").lowercase()
        if ("requires_medical_clearance" in tags) return "存在健康约束，采用前需要专业人员确认"
        if ("high_intensity" in tags && HIGH_INTENSITY_BLOCKERS.any(normalized::contains)) {
            return "与已记录的避免高强度活动约束冲突"
        }
        if ("fasting" in tags && FASTING_BLOCKERS.any(normalized::contains)) {
            return "与已记录的禁食相关健康约束冲突"
        }
        return null
    }

    private val VALID_DOMAINS = setOf("health", "career", "learning", "finance", "relationships", "leisure")
    private val VALID_ENERGY = setOf("low", "medium", "high")
    private val HIGH_INTENSITY_BLOCKERS = listOf("避免剧烈", "近期手术", "心脏", "胸痛", "骨折", "怀孕")
    private val FASTING_BLOCKERS = listOf("糖尿病", "低血糖", "怀孕")
}
