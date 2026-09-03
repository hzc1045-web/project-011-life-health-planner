package com.project011.lifehealthplanner.domain

import com.project011.lifehealthplanner.data.local.CheckInEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackAdjusterTest {
    @Test
    fun difficultSkippedWeekReducesNextLoad() {
        val checkIns = listOf(
            checkIn("skipped", difficulty = 5, energy = 1),
            checkIn("partial", difficulty = 4, energy = 2),
        )
        val adjustment = FeedbackAdjuster.evaluate(checkIns)
        assertEquals(0.8, adjustment.loadFactor, 0.0)
        assertTrue(adjustment.guidance.contains("减少"))
    }

    @Test
    fun sustainableWeekKeepsCurrentLoad() {
        val adjustment = FeedbackAdjuster.evaluate(listOf(checkIn("completed", 3, 3)))
        assertEquals(1.0, adjustment.loadFactor, 0.0)
    }

    private fun checkIn(status: String, difficulty: Int, energy: Int) = CheckInEntity(
        id = "$status-$difficulty-$energy",
        planItemId = "item",
        status = status,
        difficulty = difficulty,
        energy = energy,
        note = "",
    )
}
