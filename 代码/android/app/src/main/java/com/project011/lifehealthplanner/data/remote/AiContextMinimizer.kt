package com.project011.lifehealthplanner.data.remote

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.project011.lifehealthplanner.data.local.HealthRecordEntity
import com.project011.lifehealthplanner.data.local.LifeGoalEntity
import com.project011.lifehealthplanner.data.local.UserProfileEntity
import java.time.Instant
import java.time.ZoneId

object AiContextMinimizer {
    fun build(
        profile: UserProfileEntity,
        health: List<HealthRecordEntity>,
        goals: List<LifeGoalEntity>,
        busyBlocks: List<BusyBlockDto>,
        recentFeedback: List<String>,
        gson: Gson = Gson(),
    ) = AiContextSnapshotDto(
        ageBand = profile.ageBand,
        timezone = profile.timezone.ifBlank { ZoneId.systemDefault().id },
        region = profile.region,
        healthConstraints = parseStrings(profile.conditionsJson, gson),
        scheduleConstraints = listOfNotNull(profile.workSchedule.takeIf(String::isNotBlank)),
        preferences = parseStrings(profile.preferencesJson, gson),
        goals = goals.filter { it.status == "active" }.map {
            GoalSnapshotDto(
                id = it.id,
                domain = it.domain,
                title = it.title,
                target = it.target,
                priority = it.priority,
                targetDate = it.targetDate?.let { value -> Instant.ofEpochMilli(value).toString() },
            )
        },
        metrics = health.take(100).map {
            MetricSnapshotDto(
                kind = it.kind,
                value = it.value,
                unit = it.unit,
                measuredAt = Instant.ofEpochMilli(it.observedAt).toString(),
            )
        },
        busyBlocks = busyBlocks,
        weeklyBudget = profile.weeklyBudget,
        recentFeedback = recentFeedback,
        emergencyNumber = "",
    )

    private fun parseStrings(json: String, gson: Gson): List<String> = runCatching {
        gson.fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type)
    }.getOrDefault(emptyList())
}
