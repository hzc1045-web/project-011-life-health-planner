package com.project011.lifehealthplanner.data.remote

import com.google.gson.Gson
import com.project011.lifehealthplanner.data.local.UserProfileEntity
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
}
