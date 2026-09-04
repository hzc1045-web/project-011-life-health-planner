package com.project011.lifehealthplanner.data

import com.google.gson.Gson
import com.project011.lifehealthplanner.data.local.CalendarLinkEntity
import com.project011.lifehealthplanner.data.local.PlanItemEntity
import com.project011.lifehealthplanner.notifications.ReminderPolicy
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

internal data class RestoredPlanAction(
    val item: PlanItemEntity,
    val reminderMinutes: List<Int>,
)

data class PlanIntegrationResult(
    val calendarCount: Int,
    val reminderCount: Int,
    val calendarPendingCount: Int,
    val reminderFailureCount: Int,
) {
    fun userMessage(): String = buildString {
        append("日历 $calendarCount 项，提醒 $reminderCount 个")
        if (calendarPendingCount > 0) append("；日历待补建 $calendarPendingCount 项")
        if (reminderFailureCount > 0) append("；提醒待重试 $reminderFailureCount 个")
    }
}

internal object RestorePlanPolicy {
    fun actions(
        items: List<PlanItemEntity>,
        now: Long,
        gson: Gson = Gson(),
    ): List<RestoredPlanAction> = items
        .filter { it.status == "planned" && it.startAt > now }
        .map { item ->
            val reminderMinutes = ReminderPolicy.parseMinutes(item.reminderMinutesJson, gson)
                .filter { minutes ->
                    item.startAt - TimeUnit.MINUTES.toMillis(minutes.toLong()) > now
                }
            RestoredPlanAction(item, reminderMinutes)
        }
}

internal interface RestorePlanEffects {
    fun cancelReminders(itemId: String)

    fun deleteCalendarEvent(link: CalendarLinkEntity): Boolean

    suspend fun replaceCalendarLinks(links: List<CalendarLinkEntity>)

    fun insertCalendarEvent(item: PlanItemEntity): CalendarLinkEntity?

    suspend fun saveCalendarLink(link: CalendarLinkEntity)

    fun scheduleReminder(item: PlanItemEntity, reminderMinutes: Int)
}

internal object RestorePlanReconciler {
    suspend fun reconcile(
        oldPlanItemIds: List<String>,
        oldCalendarLinks: List<CalendarLinkEntity>,
        actions: List<RestoredPlanAction>,
        replaceStoredLinks: Boolean,
        effects: RestorePlanEffects,
    ): PlanIntegrationResult {
        oldPlanItemIds.distinct().forEach { itemId ->
            attempt { effects.cancelReminders(itemId) }
        }

        val retainedLinks = if (replaceStoredLinks) {
            oldCalendarLinks.filter { link ->
                attempt { effects.deleteCalendarEvent(link) }.getOrNull() != true
            }
        } else {
            emptyList()
        }
        if (replaceStoredLinks) effects.replaceCalendarLinks(retainedLinks)

        val retainedItemIds = retainedLinks.mapTo(hashSetOf()) { it.planItemId }
        var calendarCount = 0
        var reminderCount = 0
        var calendarPendingCount = retainedLinks.size
        var reminderFailureCount = 0
        actions.forEach { action ->
            if (action.item.id !in retainedItemIds) {
                val newLink = attempt { effects.insertCalendarEvent(action.item) }.getOrNull()
                if (newLink == null) {
                    calendarPendingCount += 1
                } else {
                    val saved = attemptSuspend { effects.saveCalendarLink(newLink) }.isSuccess
                    if (saved) {
                        calendarCount += 1
                    } else {
                        calendarPendingCount += 1
                        attempt { effects.deleteCalendarEvent(newLink) }
                    }
                }
            }
            action.reminderMinutes.forEach { minutes ->
                if (attempt { effects.scheduleReminder(action.item, minutes) }.isSuccess) {
                    reminderCount += 1
                } else {
                    reminderFailureCount += 1
                }
            }
        }
        return PlanIntegrationResult(
            calendarCount = calendarCount,
            reminderCount = reminderCount,
            calendarPendingCount = calendarPendingCount,
            reminderFailureCount = reminderFailureCount,
        )
    }

    private inline fun <T> attempt(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    private suspend inline fun <T> attemptSuspend(crossinline block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}
