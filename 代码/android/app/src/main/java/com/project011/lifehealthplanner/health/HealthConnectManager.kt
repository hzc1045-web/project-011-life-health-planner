package com.project011.lifehealthplanner.health

import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.core.content.ContextCompat
import com.project011.lifehealthplanner.data.local.HealthRecordEntity
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

class HealthConnectManager(private val context: Context) {
    val permissions: Set<String> = HealthDataType.entries.mapTo(linkedSetOf()) { it.permission }
    private val onDeviceStepCounter by lazy { OnDeviceStepCounter(context) }

    fun sdkStatus(): Int = runCatching {
        HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)
    }.getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun grantedPermissionCount(): Int {
        if (sdkStatus() != HealthConnectClient.SDK_AVAILABLE) return 0
        val granted = client().permissionController.getGrantedPermissions()
        return permissions.count(granted::contains)
    }

    /** Whether the phone exposes a hardware TYPE_STEP_COUNTER sensor. */
    fun onDeviceStepCountingAvailable(): Boolean = onDeviceStepCounter.isAvailable

    /** ACTIVITY_RECOGNITION is a runtime permission on Android 10 and newer. */
    fun onDeviceStepPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED

    /** Reads one foreground sample from the phone sensor, independent of Health Connect. */
    suspend fun readOnDeviceSteps(): OnDeviceStepSnapshot {
        check(onDeviceStepPermissionGranted()) {
            "需要授予“身体活动”权限后才能读取本机步数"
        }
        return onDeviceStepCounter.readOnce()
    }

    fun openManagement() {
        check(sdkStatus() == HealthConnectClient.SDK_AVAILABLE) { "Health Connect 当前不可用" }
        val intent = HealthConnectClient.getHealthConnectManageDataIntent(context, PROVIDER_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .getOrElse { error("无法打开 Health Connect 管理页，请在系统设置中搜索 Health Connect") }
    }

    suspend fun readRecent(days: Long = 30): HealthSyncResult {
        check(sdkStatus() == HealthConnectClient.SDK_AVAILABLE) { "此设备当前无法使用 Health Connect" }
        val healthClient = client()
        val grantedPermissions = healthClient.permissionController.getGrantedPermissions()
        val grantedTypes = HealthDataType.entries
            .filterTo(linkedSetOf()) { it.permission in grantedPermissions }
        val end = Instant.now()
        val start = end.minus(days, ChronoUnit.DAYS)
        val filter = TimeRangeFilter.between(start, end)
        return collectGrantedHealthData(
            requestedTypes = HealthDataType.entries,
            grantedTypes = grantedTypes,
            label = HealthDataType::label,
        ) { type ->
            readType(healthClient, type, filter)
        }
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    private suspend fun readType(
        healthClient: HealthConnectClient,
        type: HealthDataType,
        filter: TimeRangeFilter,
    ): List<HealthRecordEntity> = when (type) {
        HealthDataType.STEPS -> readAll(healthClient, StepsRecord::class, filter).map {
            record("steps", it.metadata.id, it.count.toDouble(), "步", it.endTime, it.metadata.dataOrigin.packageName)
        }
        HealthDataType.DISTANCE -> readAll(healthClient, DistanceRecord::class, filter).map {
            record("distance", it.metadata.id, it.distance.inKilometers, "km", it.endTime, it.metadata.dataOrigin.packageName)
        }
        HealthDataType.ACTIVE_CALORIES -> readAll(healthClient, ActiveCaloriesBurnedRecord::class, filter).map {
            record(
                "active_calories",
                it.metadata.id,
                it.energy.inKilocalories,
                "kcal",
                it.endTime,
                it.metadata.dataOrigin.packageName,
            )
        }
        HealthDataType.SLEEP -> readAll(healthClient, SleepSessionRecord::class, filter).map {
            val hours = Duration.between(it.startTime, it.endTime).toMinutes() / 60.0
            record("sleep", it.metadata.id, hours, "小时", it.endTime, it.metadata.dataOrigin.packageName)
        }
        HealthDataType.HEART_RATE -> readAll(healthClient, HeartRateRecord::class, filter).mapNotNull { entry ->
            entry.samples.lastOrNull()?.let {
                record(
                    "heart_rate",
                    entry.metadata.id,
                    it.beatsPerMinute.toDouble(),
                    "bpm",
                    it.time,
                    entry.metadata.dataOrigin.packageName,
                )
            }
        }
        HealthDataType.RESTING_HEART_RATE -> readAll(healthClient, RestingHeartRateRecord::class, filter).map {
            record(
                "resting_heart_rate",
                it.metadata.id,
                it.beatsPerMinute.toDouble(),
                "bpm",
                it.time,
                it.metadata.dataOrigin.packageName,
            )
        }
        HealthDataType.WEIGHT -> readAll(healthClient, WeightRecord::class, filter).map {
            record("weight", it.metadata.id, it.weight.inKilograms, "kg", it.time, it.metadata.dataOrigin.packageName)
        }
    }

    private suspend fun <T : Record> readAll(
        healthClient: HealthConnectClient,
        recordType: KClass<T>,
        filter: TimeRangeFilter,
    ): List<T> = buildList {
        var pageToken: String? = null
        val seenPageTokens = mutableSetOf<String>()
        var pageCount = 0
        do {
            pageCount += 1
            check(pageCount <= MAX_PAGE_COUNT) { "Health Connect 分页数量异常" }
            pageToken?.let { token ->
                check(seenPageTokens.add(token)) { "Health Connect 返回了重复分页令牌" }
            }
            val response = healthClient.readRecords(
                ReadRecordsRequest(recordType, filter, pageToken = pageToken),
            )
            addAll(response.records)
            pageToken = response.pageToken
            // Some Health Connect provider versions use an empty token instead of null
            // to signal that the final page has been returned.
        } while (hasNextPage(pageToken))
    }

    private fun record(
        kind: String,
        externalId: String,
        value: Double,
        unit: String,
        time: Instant,
        source: String,
    ) = HealthRecordEntity(
        id = HealthRecordIdentity.create(kind, externalId, source, time),
        kind = kind,
        value = value,
        unit = unit,
        observedAt = time.toEpochMilli(),
        source = "health_connect:$source",
    )

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
        private const val MAX_PAGE_COUNT = 1_000
    }
}

fun hasNextPage(pageToken: String?): Boolean = !pageToken.isNullOrBlank()

private enum class HealthDataType(val label: String, val permission: String) {
    STEPS("步数", HealthPermission.getReadPermission(StepsRecord::class)),
    DISTANCE("距离", HealthPermission.getReadPermission(DistanceRecord::class)),
    ACTIVE_CALORIES("活动热量", HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)),
    SLEEP("睡眠", HealthPermission.getReadPermission(SleepSessionRecord::class)),
    HEART_RATE("心率", HealthPermission.getReadPermission(HeartRateRecord::class)),
    RESTING_HEART_RATE("静息心率", HealthPermission.getReadPermission(RestingHeartRateRecord::class)),
    WEIGHT("体重", HealthPermission.getReadPermission(WeightRecord::class)),
}
