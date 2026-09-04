package com.project011.lifehealthplanner.data.remote

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.project011.lifehealthplanner.data.local.HealthRecordEntity
import com.project011.lifehealthplanner.data.local.LifeGoalEntity
import com.project011.lifehealthplanner.data.local.MedicationEntity
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
        medications: List<MedicationEntity> = emptyList(),
        gson: Gson = Gson(),
    ): AiContextSnapshotDto {
        val allergies = parseStrings(profile.allergiesJson, gson)
        val healthConstraints = buildList {
            addAll(parseStrings(profile.conditionsJson, gson))
            addAll(allergies.map { "已知过敏：$it" })
            addAll(medications.map { medication ->
                buildString {
                    append("当前用药：${medication.name.take(60)}")
                    if (medication.activeIngredient.isNotBlank()) {
                        append("（${medication.activeIngredient.take(60)}）")
                    }
                    if (medication.schedule.isNotBlank()) append("；安排：${medication.schedule.take(60)}")
                    append("；不得调整剂量")
                }
            })
        }.map { it.take(200) }.take(30)
        val profileMetrics = buildList {
            profile.heightCm?.let {
                add(MetricSnapshotDto("height", it, "cm", Instant.ofEpochMilli(profile.updatedAt).toString()))
            }
            if (health.none { it.kind == "weight" }) {
                profile.weightKg?.let {
                    add(MetricSnapshotDto("weight", it, "kg", Instant.ofEpochMilli(profile.updatedAt).toString()))
                }
            }
        }
        val recentMetrics = health.map {
            MetricSnapshotDto(
                kind = it.kind,
                value = it.value,
                unit = it.unit,
                measuredAt = Instant.ofEpochMilli(it.observedAt).toString(),
            )
        }
        return AiContextSnapshotDto(
        ageBand = profile.ageBand,
        timezone = profile.timezone.ifBlank { ZoneId.systemDefault().id },
        region = profile.region.ifBlank { "未提供" },
        healthConstraints = healthConstraints,
        scheduleConstraints = listOfNotNull(profile.workSchedule.takeIf(String::isNotBlank)?.take(200)).take(30),
        preferences = (
            parseStrings(profile.preferencesJson, gson) +
                "目标睡眠时长 ${"%.1f".format(profile.sleepHours)} 小时"
            ).map { it.take(200) }.take(30),
        goals = goals.filter { it.status == "active" }.take(50).map {
            GoalSnapshotDto(
                id = it.id,
                domain = it.domain,
                title = it.title,
                target = it.target,
                priority = it.priority,
                targetDate = it.targetDate?.let { value -> Instant.ofEpochMilli(value).toString() },
            )
        },
        metrics = (profileMetrics + recentMetrics).take(100),
        busyBlocks = busyBlocks.take(100),
        weeklyBudget = profile.weeklyBudget,
        recentFeedback = recentFeedback.map { it.take(200) }.take(50),
        emergencyNumber = "",
    )
    }

    private fun parseStrings(json: String, gson: Gson): List<String> = runCatching {
        gson.fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type)
    }.getOrDefault(emptyList())
}
