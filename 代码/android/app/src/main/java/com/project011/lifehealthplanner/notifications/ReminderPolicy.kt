package com.project011.lifehealthplanner.notifications

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

internal object ReminderPolicy {
    fun parseMinutes(json: String, gson: Gson = Gson()): List<Int> = runCatching {
        gson.fromJson<List<Int>>(json, object : TypeToken<List<Int>>() {}.type)
            .filter { it in 0..1_440 }
            .distinct()
    }.getOrDefault(emptyList())

    fun shouldCancel(status: String): Boolean = status in setOf("completed", "partial", "skipped")

    fun itemTag(itemId: String): String = "plan-item-$itemId"

    fun workName(itemId: String, reminderMinutes: Int): String =
        "plan-reminder-$itemId-$reminderMinutes"
}
