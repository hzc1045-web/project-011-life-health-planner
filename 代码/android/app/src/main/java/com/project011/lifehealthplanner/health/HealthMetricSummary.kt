package com.project011.lifehealthplanner.health

import com.project011.lifehealthplanner.data.IQOO_WATCH_DAILY_SOURCE
import com.project011.lifehealthplanner.data.local.HealthRecordEntity
import java.time.Instant
import java.time.ZoneId

enum class HealthMetricDisplayMode {
    TODAY_TOTAL,
    DEVICE_SINCE_BOOT,
    LATEST_READING,
    LATEST_SESSION,
}

data class HealthMetricSummary(
    val kind: String,
    val value: Double,
    val unit: String,
    val observedAt: Long,
    val displayMode: HealthMetricDisplayMode,
)

object HealthMetricSummaryPolicy {
    private val cumulativeKinds = setOf("steps", "distance", "active_calories")

    fun summarize(
        records: List<HealthRecordEntity>,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<HealthMetricSummary> {
        val nowMillis = now.toEpochMilli()
        val today = now.atZone(zoneId).toLocalDate()
        val dayStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val eligible = records.filter { record ->
            record.observedAt <= nowMillis && record.value.isFinite()
        }

        val totals = cumulativeKinds.mapNotNull { kind ->
            val todayRecords = eligible.filter { record ->
                record.kind == kind && record.observedAt >= dayStart && record.value >= 0.0
            }

            // TYPE_STEP_COUNTER reports one cumulative snapshot since boot. Keep only
            // the newest snapshot; summing repeated foreground reads would inflate steps.
            val deviceStep = if (kind == "steps") {
                eligible
                    .filter { it.isOnDeviceStepSnapshot() && it.value >= 0.0 }
                    .maxByOrNull(HealthRecordEntity::observedAt)
            } else {
                null
            }
            val healthConnectRecords = todayRecords.filter { it.isHealthConnectRecord() }
            val iqooDailyRecords = todayRecords.filter { it.source == IQOO_WATCH_DAILY_SOURCE }
            val selected = when {
                kind == "steps" && healthConnectRecords.isNotEmpty() -> healthConnectRecords
                iqooDailyRecords.isNotEmpty() -> iqooDailyRecords
                kind == "steps" && deviceStep != null -> listOf(deviceStep)
                else -> todayRecords
                    // Prefer synchronized values when both synchronized and manual
                    // values exist, so a correction is not counted on top of them.
                    .filter { it.isHealthConnectRecord() }
                    .ifEmpty { todayRecords }
            }
            val latest = selected.maxByOrNull(HealthRecordEntity::observedAt)
                ?: return@mapNotNull null
            val displayMode = if (
                kind == "steps" && deviceStep != null &&
                healthConnectRecords.isEmpty() && iqooDailyRecords.isEmpty()
            ) {
                HealthMetricDisplayMode.DEVICE_SINCE_BOOT
            } else {
                HealthMetricDisplayMode.TODAY_TOTAL
            }
            HealthMetricSummary(
                kind = kind,
                value = if (displayMode == HealthMetricDisplayMode.DEVICE_SINCE_BOOT) {
                    latest.value
                } else {
                    selected.sumOf(HealthRecordEntity::value)
                },
                unit = latest.unit,
                observedAt = latest.observedAt,
                displayMode = displayMode,
            )
        }

        val latest = eligible
            .asSequence()
            .filterNot { it.kind in cumulativeKinds }
            .filterNot { it.kind == "sleep" && it.value <= 0.0 }
            .groupBy(HealthRecordEntity::kind)
            .values
            .mapNotNull { values -> values.maxByOrNull(HealthRecordEntity::observedAt) }
            .map { record ->
                HealthMetricSummary(
                    kind = record.kind,
                    value = record.value,
                    unit = record.unit,
                    observedAt = record.observedAt,
                    displayMode = if (record.kind == "sleep") {
                        HealthMetricDisplayMode.LATEST_SESSION
                    } else {
                        HealthMetricDisplayMode.LATEST_READING
                    },
                )
            }

        return (totals + latest).sortedByDescending(HealthMetricSummary::observedAt)
    }

    private fun HealthRecordEntity.isHealthConnectRecord(): Boolean =
        source.startsWith("health_connect:")

    private fun HealthRecordEntity.isOnDeviceStepSnapshot(): Boolean =
        kind == "steps" && source == OnDeviceStepSnapshot.SOURCE
}
