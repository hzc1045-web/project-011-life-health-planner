package com.project011.lifehealthplanner.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.project011.lifehealthplanner.data.local.HealthRecordEntity
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class HealthConnectManager(private val context: Context) {
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
    )

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun hasAllPermissions(): Boolean {
        if (sdkStatus() != HealthConnectClient.SDK_AVAILABLE) return false
        return client().permissionController.getGrantedPermissions().containsAll(permissions)
    }

    suspend fun readRecent(days: Long = 7): List<HealthRecordEntity> {
        if (!hasAllPermissions()) return emptyList()
        val end = Instant.now()
        val start = end.minus(days, ChronoUnit.DAYS)
        val filter = TimeRangeFilter.between(start, end)
        val result = mutableListOf<HealthRecordEntity>()
        client().readRecords(ReadRecordsRequest(StepsRecord::class, filter)).records.forEach {
            result += record("steps", it.count.toDouble(), "步", it.endTime, it.metadata.dataOrigin.packageName)
        }
        client().readRecords(ReadRecordsRequest(DistanceRecord::class, filter)).records.forEach {
            result += record("distance", it.distance.inKilometers, "km", it.endTime, it.metadata.dataOrigin.packageName)
        }
        client().readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, filter)).records.forEach {
            result += record("active_calories", it.energy.inKilocalories, "kcal", it.endTime, it.metadata.dataOrigin.packageName)
        }
        client().readRecords(ReadRecordsRequest(SleepSessionRecord::class, filter)).records.forEach {
            val hours = Duration.between(it.startTime, it.endTime).toMinutes() / 60.0
            result += record("sleep", hours, "小时", it.endTime, it.metadata.dataOrigin.packageName)
        }
        client().readRecords(ReadRecordsRequest(HeartRateRecord::class, filter)).records.forEach { entry ->
            entry.samples.lastOrNull()?.let {
                result += record("heart_rate", it.beatsPerMinute.toDouble(), "bpm", it.time, entry.metadata.dataOrigin.packageName)
            }
        }
        client().readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, filter)).records.forEach {
            result += record("resting_heart_rate", it.beatsPerMinute.toDouble(), "bpm", it.time, it.metadata.dataOrigin.packageName)
        }
        client().readRecords(ReadRecordsRequest(WeightRecord::class, filter)).records.forEach {
            result += record("weight", it.weight.inKilograms, "kg", it.time, it.metadata.dataOrigin.packageName)
        }
        return result
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    private fun record(
        kind: String,
        value: Double,
        unit: String,
        time: Instant,
        source: String,
    ) = HealthRecordEntity(
        id = "hc-${UUID.randomUUID()}",
        kind = kind,
        value = value,
        unit = unit,
        observedAt = time.toEpochMilli(),
        source = "health_connect:$source",
    )

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
    }
}
