package com.project011.lifehealthplanner.domain

import com.project011.lifehealthplanner.data.local.HealthRecordEntity
import java.time.Duration
import java.time.Instant

enum class RiskLevel { NORMAL, CAUTION, URGENT }

data class PlanningRiskAssessment(
    val level: RiskLevel,
    val message: String = "",
)

object RiskDetector {
    private val urgent = listOf(
        Regex("胸痛.*(呼吸|出汗|晕)"),
        Regex("呼吸(非常|严重)?困难"),
        Regex("无法呼吸"),
        Regex("一侧.*(无力|麻木)"),
        Regex("言语不清"),
        Regex("大量出血"),
        Regex("失去意识"),
        Regex("自杀|轻生|伤害自己"),
    )
    private val caution = listOf(
        Regex("持续发烧"),
        Regex("反复头晕"),
        Regex("药物.*(过敏|皮疹)"),
        Regex("血压.*(很高|很低)"),
    )

    fun detect(text: String): RiskLevel = when {
        urgent.any { it.containsMatchIn(text) } -> RiskLevel.URGENT
        caution.any { it.containsMatchIn(text) } -> RiskLevel.CAUTION
        else -> RiskLevel.NORMAL
    }

    /**
     * Combines the free-text focus/profile with recent manually entered or
     * Health Connect metrics. This is a conservative safety gate, not a
     * diagnosis engine: only clearly extreme values are marked urgent.
     */
    fun assessForPlanning(
        focus: String,
        profileText: String,
        healthRecords: Iterable<HealthRecordEntity> = emptyList(),
        now: Instant = Instant.now(),
    ): PlanningRiskAssessment {
        val textRisk = detect("$focus\n$profileText")
        if (textRisk == RiskLevel.URGENT) {
            return PlanningRiskAssessment(
                RiskLevel.URGENT,
                "画像或计划描述包含需要立即关注的健康风险",
            )
        }

        val metricAssessment = assessRecentMetrics(healthRecords, now)
        if (metricAssessment.level == RiskLevel.URGENT) return metricAssessment
        return if (metricAssessment.level == RiskLevel.CAUTION || textRisk == RiskLevel.CAUTION) {
            PlanningRiskAssessment(RiskLevel.CAUTION, "近期健康信息提示需要谨慎安排")
        } else {
            PlanningRiskAssessment(RiskLevel.NORMAL)
        }
    }

    fun detectHealthRecords(
        healthRecords: Iterable<HealthRecordEntity>,
        now: Instant = Instant.now(),
    ): RiskLevel = assessRecentMetrics(healthRecords, now).level

    private fun assessRecentMetrics(
        healthRecords: Iterable<HealthRecordEntity>,
        now: Instant,
    ): PlanningRiskAssessment {
        val cutoff = now.minus(RECENT_METRIC_WINDOW)
        val recent = healthRecords.filter { record ->
            record.observedAt in cutoff.toEpochMilli()..now.toEpochMilli() && record.value.isFinite()
        }
        recent.forEach { record ->
            val level = metricRisk(record.kind, record.value)
            if (level == RiskLevel.URGENT) {
                return PlanningRiskAssessment(
                    RiskLevel.URGENT,
                    "近期${metricLabel(record.kind)}指标异常，已暂停普通计划并建议尽快就医",
                )
            }
        }
        if (recent.any { metricRisk(it.kind, it.value) == RiskLevel.CAUTION }) {
            return PlanningRiskAssessment(RiskLevel.CAUTION, "近期健康指标需要留意")
        }
        return PlanningRiskAssessment(RiskLevel.NORMAL)
    }

    private fun metricRisk(kind: String, value: Double): RiskLevel = when (kind.lowercase()) {
        "oxygen_saturation", "spo2", "blood_oxygen" -> when {
            value < 90.0 -> RiskLevel.URGENT
            value < 94.0 -> RiskLevel.CAUTION
            else -> RiskLevel.NORMAL
        }
        "heart_rate", "resting_heart_rate" -> when {
            value >= 180.0 || value <= 35.0 -> RiskLevel.URGENT
            value >= 120.0 || value <= 45.0 -> RiskLevel.CAUTION
            else -> RiskLevel.NORMAL
        }
        "blood_pressure_systolic", "systolic_bp" -> when {
            value >= 180.0 || value <= 80.0 -> RiskLevel.URGENT
            value >= 140.0 || value <= 90.0 -> RiskLevel.CAUTION
            else -> RiskLevel.NORMAL
        }
        "blood_pressure_diastolic", "diastolic_bp" -> when {
            value >= 120.0 || value <= 40.0 -> RiskLevel.URGENT
            value >= 90.0 || value <= 60.0 -> RiskLevel.CAUTION
            else -> RiskLevel.NORMAL
        }
        "blood_glucose", "glucose", "blood_sugar" -> when {
            value >= 300.0 || value <= 50.0 -> RiskLevel.URGENT
            value >= 200.0 || value <= 70.0 -> RiskLevel.CAUTION
            else -> RiskLevel.NORMAL
        }
        else -> RiskLevel.NORMAL
    }

    private fun metricLabel(kind: String): String = when (kind.lowercase()) {
        "oxygen_saturation", "spo2", "blood_oxygen" -> "血氧"
        "heart_rate" -> "心率"
        "resting_heart_rate" -> "静息心率"
        "blood_pressure_systolic", "systolic_bp" -> "收缩压"
        "blood_pressure_diastolic", "diastolic_bp" -> "舒张压"
        "blood_glucose", "glucose", "blood_sugar" -> "血糖"
        else -> "健康"
    }

    private val RECENT_METRIC_WINDOW: Duration = Duration.ofHours(48)
}
