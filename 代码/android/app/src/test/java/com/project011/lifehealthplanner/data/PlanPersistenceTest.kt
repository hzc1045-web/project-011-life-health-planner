package com.project011.lifehealthplanner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlanPersistenceTest {
    @Test
    fun sameDraftItemIdIsUniqueAcrossConfirmedPlans() {
        val first = PlanPersistence.itemId("plan-one", "item-1")
        val second = PlanPersistence.itemId("plan-two", "item-1")

        assertNotEquals(first, second)
        assertEquals("plan-one:item-1", first)
        assertEquals(first, PlanPersistence.itemId("plan-one", "item-1"))
    }
}
