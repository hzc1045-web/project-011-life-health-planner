package com.project011.lifehealthplanner.data

import com.project011.lifehealthplanner.data.local.CalendarLinkEntity
import com.project011.lifehealthplanner.data.local.PlanItemEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class RestorePlanPolicyTest {
    private val now = 1_800_000_000_000L

    @Test
    fun onlyFuturePlannedItemsAreRebuiltWithFutureValidReminders() {
        val future = item("future", "planned", now + TimeUnit.MINUTES.toMillis(30), "[15,30,31,0,15,1441]")
        val actions = RestorePlanPolicy.actions(
            listOf(
                future,
                item("completed", "completed", now + TimeUnit.HOURS.toMillis(1)),
                item("partial", "partial", now + TimeUnit.HOURS.toMillis(1)),
                item("past", "planned", now - 1),
                item("now", "planned", now),
            ),
            now,
        )

        assertEquals(listOf("future"), actions.map { it.item.id })
        assertEquals(listOf(15, 0), actions.single().reminderMinutes)
    }

    @Test
    fun oneExternalFailureDoesNotStopRemainingCleanupOrRebuild() = runTest {
        val oldLinks = listOf(
            CalendarLinkEntity("old-a", 1, 10),
            CalendarLinkEntity("old-b", 2, 10),
        )
        val first = RestoredPlanAction(item("new-a"), listOf(15, 5))
        val second = RestoredPlanAction(item("new-b"), listOf(10))
        val calls = mutableListOf<String>()
        val effects = object : RestorePlanEffects {
            override fun cancelReminders(itemId: String) {
                calls += "cancel:$itemId"
                if (itemId == "old-a") error("cancel failed")
            }

            override fun deleteCalendarEvent(link: CalendarLinkEntity): Boolean {
                calls += "delete:${link.planItemId}"
                if (link.planItemId == "old-a") error("delete failed")
                return true
            }

            override suspend fun replaceCalendarLinks(links: List<CalendarLinkEntity>) {
                calls += "replace:${links.joinToString { it.planItemId }}"
            }

            override fun insertCalendarEvent(item: PlanItemEntity): CalendarLinkEntity? {
                calls += "insert:${item.id}"
                if (item.id == "new-a") error("insert failed")
                return CalendarLinkEntity(item.id, 20, 10)
            }

            override suspend fun saveCalendarLink(link: CalendarLinkEntity) {
                calls += "save:${link.planItemId}"
                error("save failed")
            }

            override fun scheduleReminder(item: PlanItemEntity, reminderMinutes: Int) {
                calls += "schedule:${item.id}:$reminderMinutes"
                if (item.id == "new-a" && reminderMinutes == 15) error("schedule failed")
            }
        }

        val result = RestorePlanReconciler.reconcile(
            oldPlanItemIds = listOf("old-a", "old-a", "old-b"),
            oldCalendarLinks = oldLinks,
            actions = listOf(first, second),
            replaceStoredLinks = true,
            effects = effects,
        )

        assertEquals(1, calls.count { it == "cancel:old-a" })
        assertTrue("cancel:old-b" in calls)
        assertTrue("delete:old-b" in calls)
        assertTrue("replace:old-a" in calls)
        assertTrue("insert:new-b" in calls)
        assertTrue("schedule:new-a:5" in calls)
        assertTrue("schedule:new-b:10" in calls)
        assertTrue("save:new-b" in calls)
        assertTrue("delete:new-b" in calls)
        assertEquals(PlanIntegrationResult(0, 2, 3, 1), result)
    }

    @Test
    fun appendCountsOnlySuccessfullySavedCalendarLinksAndScheduledReminders() = runTest {
        val action = RestoredPlanAction(item("new"), listOf(15, 5))
        var replaced = false
        val effects = object : RestorePlanEffects {
            override fun cancelReminders(itemId: String) = Unit

            override fun deleteCalendarEvent(link: CalendarLinkEntity) = true

            override suspend fun replaceCalendarLinks(links: List<CalendarLinkEntity>) {
                replaced = true
            }

            override fun insertCalendarEvent(item: PlanItemEntity) = CalendarLinkEntity(item.id, 20, 10)

            override suspend fun saveCalendarLink(link: CalendarLinkEntity) = Unit

            override fun scheduleReminder(item: PlanItemEntity, reminderMinutes: Int) {
                if (reminderMinutes == 15) error("work manager unavailable")
            }
        }

        val result = RestorePlanReconciler.reconcile(
            oldPlanItemIds = emptyList(),
            oldCalendarLinks = emptyList(),
            actions = listOf(action),
            replaceStoredLinks = false,
            effects = effects,
        )

        assertEquals(PlanIntegrationResult(1, 1, 0, 1), result)
        assertTrue(!replaced)
    }

    @Test
    fun cancellationFromSuspendCalendarWriteIsPropagated() = runTest {
        val effects = object : RestorePlanEffects {
            override fun cancelReminders(itemId: String) = Unit

            override fun deleteCalendarEvent(link: CalendarLinkEntity) = true

            override suspend fun replaceCalendarLinks(links: List<CalendarLinkEntity>) = Unit

            override fun insertCalendarEvent(item: PlanItemEntity) = CalendarLinkEntity(item.id, 20, 10)

            override suspend fun saveCalendarLink(link: CalendarLinkEntity) {
                throw CancellationException("cancelled")
            }

            override fun scheduleReminder(item: PlanItemEntity, reminderMinutes: Int) = Unit
        }
        var cancelled = false

        try {
            RestorePlanReconciler.reconcile(
                oldPlanItemIds = emptyList(),
                oldCalendarLinks = emptyList(),
                actions = listOf(RestoredPlanAction(item("new"), emptyList())),
                replaceStoredLinks = false,
                effects = effects,
            )
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
    }

    private fun item(
        id: String,
        status: String = "planned",
        startAt: Long = now + TimeUnit.HOURS.toMillis(2),
        reminders: String = "[]",
    ) = PlanItemEntity(
        id = id,
        planId = "plan",
        domain = "health",
        title = id,
        description = "",
        startAt = startAt,
        endAt = startAt + TimeUnit.MINUTES.toMillis(30),
        priority = 3,
        energy = "medium",
        estimatedCost = 0.0,
        reminderMinutesJson = reminders,
        status = status,
    )
}
