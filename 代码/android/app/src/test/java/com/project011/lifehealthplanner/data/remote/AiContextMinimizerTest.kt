package com.project011.lifehealthplanner.data.remote

import com.google.gson.Gson
import com.project011.lifehealthplanner.data.local.UserProfileEntity
import com.project011.lifehealthplanner.data.local.MedicationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiContextMinimizerTest {
    @Test
    fun personalIdentifiersAreNotIncluded() {
        val profile = UserProfileEntity(
            displayName = "PRIVATE_NAME_9182",
            birthDate = "1991-02-03",
            ageBand = "30-39",
            region = "中国",
            emergencyNumber = "PRIVATE_CONTACT_120",
            workSchedule = "工作日 09:00-18:00",
            onboardingComplete = true,
        )
        val json = Gson().toJson(
            AiContextMinimizer.build(profile, emptyList(), emptyList(), emptyList(), emptyList()),
        )
        assertFalse(json.contains("PRIVATE_NAME_9182"))
        assertFalse(json.contains("1991-02-03"))
        assertFalse(json.contains("PRIVATE_CONTACT_120"))
        assertTrue(json.contains("30-39"))
    }

    @Test
    fun planningContextIncludesBodyMetricsAllergiesMedicationAndSleepTarget() {
        val profile = UserProfileEntity(
            ageBand = "30-39",
            region = "中国",
            heightCm = 180.0,
            weightKg = 75.0,
            allergiesJson = "[\"青霉素\"]",
            sleepHours = 7.5,
            onboardingComplete = true,
        )
        val context = AiContextMinimizer.build(
            profile,
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            medications = listOf(
                MedicationEntity(
                    id = "med-1",
                    name = "测试药物",
                    activeIngredient = "测试成分",
                    dose = "不发送剂量",
                    schedule = "早餐后",
                ),
            ),
        )

        assertEquals(setOf("height", "weight"), context.metrics.map { it.kind }.toSet())
        assertTrue(context.healthConstraints.any { "青霉素" in it })
        assertTrue(context.healthConstraints.any { "测试药物" in it && "不得调整剂量" in it })
        assertTrue(context.preferences.any { "7.5" in it })
        assertFalse(Gson().toJson(context).contains("不发送剂量"))
    }
}
