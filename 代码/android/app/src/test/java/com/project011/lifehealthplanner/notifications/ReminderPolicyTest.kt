package com.project011.lifehealthplanner.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPolicyTest {
    @Test
    fun reminderMinutesAreValidatedAndDeduplicated() {
        assertEquals(
            listOf(15, 0, 1_440),
            ReminderPolicy.parseMinutes("[15,0,15,-1,1441,1440]"),
        )
        assertEquals(emptyList<Int>(), ReminderPolicy.parseMinutes("not-json"))
        assertEquals(emptyList<Int>(), ReminderPolicy.parseMinutes("null"))
    }

    @Test
    fun terminalCheckInsCancelEveryReminderForTheItem() {
        listOf("completed", "partial", "skipped").forEach {
            assertTrue(it, ReminderPolicy.shouldCancel(it))
        }
        listOf("planned", "rescheduled", "").forEach {
            assertFalse(it, ReminderPolicy.shouldCancel(it))
        }

        val itemId = "plan-one:item-1"
        assertEquals("plan-item-$itemId", ReminderPolicy.itemTag(itemId))
        assertEquals("plan-reminder-$itemId-15", ReminderPolicy.workName(itemId, 15))
    }
}
