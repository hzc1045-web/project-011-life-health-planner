package com.project011.lifehealthplanner.domain

import com.project011.lifehealthplanner.data.local.MedicationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskAndMedicationTest {
    @Test
    fun urgentLanguageIsDetected() {
        assertEquals(RiskLevel.URGENT, RiskDetector.detect("突然胸痛并且呼吸困难、出汗"))
        assertEquals(RiskLevel.CAUTION, RiskDetector.detect("最近反复头晕"))
        assertEquals(RiskLevel.NORMAL, RiskDetector.detect("今天散步三十分钟"))
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
