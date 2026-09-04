package com.project011.lifehealthplanner.domain

import com.project011.lifehealthplanner.data.local.MedicationEntity
import com.project011.lifehealthplanner.data.local.HealthRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RiskAndMedicationTest {
    @Test
    fun urgentLanguageIsDetected() {
        assertEquals(RiskLevel.URGENT, RiskDetector.detect("突然胸痛并且呼吸困难、出汗"))
        assertEquals(RiskLevel.CAUTION, RiskDetector.detect("最近反复头晕"))
        assertEquals(RiskLevel.NORMAL, RiskDetector.detect("今天散步三十分钟"))
    }

    @Test
    fun planningRiskIncludesProfileAndRecentExtremeMetrics() {
        val now = Instant.now()
        val profileRisk = RiskDetector.assessForPlanning(
            focus = "安排轻松学习",
            profileText = "[\"胸痛并且呼吸困难\"]",
            now = now,
        )
        assertEquals(RiskLevel.URGENT, profileRisk.level)

        val metricRisk = RiskDetector.assessForPlanning(
            focus = "安排轻松学习",
            profileText = "[]",
            healthRecords = listOf(
                HealthRecordEntity(
                    id = "spo2-test",
                    kind = "oxygen_saturation",
                    value = 87.0,
                    unit = "%",
                    observedAt = now.minusSeconds(60).toEpochMilli(),
                    source = "manual",
                ),
            ),
            now = now,
        )
        assertEquals(RiskLevel.URGENT, metricRisk.level)
    }

    @Test
    fun oldExtremeMetricDoesNotBlockCurrentPlanning() {
        val now = Instant.now()
        val risk = RiskDetector.detectHealthRecords(
            listOf(
                HealthRecordEntity(
                    id = "old-heart-rate",
                    kind = "heart_rate",
                    value = 200.0,
                    unit = "bpm",
                    observedAt = now.minusSeconds(49 * 60 * 60).toEpochMilli(),
                    source = "manual",
                ),
            ),
            now,
        )
        assertEquals(RiskLevel.NORMAL, risk)
    }

    @Test
    fun duplicateIngredientAndAllergyAreWarned() {
        val medications = listOf(
            medication("退烧药甲", "对乙酰氨基酚"),
            medication("复方感冒药", "对乙酰氨基酚"),
        )
        val warnings = MedicationSafety.warnings(medications, listOf("对乙酰氨基酚"))
        assertTrue(warnings.any { "重复有效成分" in it })
        assertTrue(warnings.any { "过敏" in it })
        assertTrue(warnings.any { "专业药物相互作用数据库" in it })
    }

    private fun medication(name: String, ingredient: String) = MedicationEntity(
        id = name,
        name = name,
        activeIngredient = ingredient,
        dose = "",
        schedule = "",
    )
}
