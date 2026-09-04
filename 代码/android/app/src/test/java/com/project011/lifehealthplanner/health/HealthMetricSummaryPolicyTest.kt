package com.project011.lifehealthplanner.health

import com.project011.lifehealthplanner.data.local.HealthRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class HealthMetricSummaryPolicyTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-09-04T04:00:00Z")

    @Test
    fun cumulativeMetricsAreSummedForLocalTodayOnly() {
        val result = HealthMetricSummaryPolicy.summarize(
            records = listOf(
                record("steps", 120.0, "步", "2026-09-03T16:00:00Z"),
                record("steps", 80.0, "步", "2026-09-04T03:00:00Z"),
                record("steps", 999.0, "步", "2026-09-03T15:59:59Z"),
                record("distance", 0.4, "km", "2026-09-04T02:00:00Z"),
                record("distance", 0.6, "km", "2026-09-04T03:30:00Z"),
                record("active_calories", 25.0, "kcal", "2026-09-04T01:00:00Z"),
            ),
            now = now,
            zoneId = zone,
        )

        assertEquals(200.0, result.single { it.kind == "steps" }.value, 0.0)
        assertEquals(1.0, result.single { it.kind == "distance" }.value, 0.0)
        assertEquals(25.0, result.single { it.kind == "active_calories" }.value, 0.0)
        assertEquals(
            HealthMetricDisplayMode.TODAY_TOTAL,
            result.single { it.kind == "steps" }.displayMode,
        )
    }

    @Test
    fun vitalsStayLatestAndSleepUsesLatestCompletedSession() {
        val result = HealthMetricSummaryPolicy.summarize(
            records = listOf(
                record("weight", 70.0, "kg", "2026-09-01T00:00:00Z"),
                record("weight", 69.5, "kg", "2026-09-03T00:00:00Z"),
                record("heart_rate", 65.0, "bpm", "2026-09-04T02:00:00Z"),
                record("heart_rate", 72.0, "bpm", "2026-09-04T03:00:00Z"),
                record("sleep", 7.5, "小时", "2026-09-03T23:00:00Z"),
                record("sleep", 0.0, "小时", "2026-09-04T01:00:00Z"),
                record("sleep", 8.0, "小时", "2026-09-04T05:00:00Z"),
            ),
            now = now,
            zoneId = zone,
        )

        assertEquals(69.5, result.single { it.kind == "weight" }.value, 0.0)
        assertEquals(72.0, result.single { it.kind == "heart_rate" }.value, 0.0)
        assertEquals(7.5, result.single { it.kind == "sleep" }.value, 0.0)
        assertEquals(
            HealthMetricDisplayMode.LATEST_SESSION,
            result.single { it.kind == "sleep" }.displayMode,
        )
    }

    @Test
    fun oldCumulativeRecordsAndInvalidValuesAreNotDisplayedAsToday() {
        val result = HealthMetricSummaryPolicy.summarize(
            records = listOf(
                record("steps", 300.0, "步", "2026-09-03T15:59:59Z"),
                record("distance", Double.NaN, "km", "2026-09-04T01:00:00Z"),
                record("active_calories", -4.0, "kcal", "2026-09-04T02:00:00Z"),
            ),
            now = now,
            zoneId = zone,
        )

        assertFalse(result.any { it.kind in setOf("steps", "distance", "active_calories") })
    }

    @Test
    fun synchronizedCumulativeValuesTakePrecedenceOverManualCorrections() {
        val result = HealthMetricSummaryPolicy.summarize(
            records = listOf(
                record("steps", 100.0, "步", "2026-09-04T01:00:00Z", source = "manual"),
                record("steps", 2500.0, "步", "2026-09-04T02:00:00Z", source = "health_connect:com.google.android.apps.fitness"),
            ),
            now = now,
            zoneId = zone,
        )

        assertEquals(2500.0, result.single { it.kind == "steps" }.value, 0.0)
    }

    @Test
    fun deviceStepSnapshotsUseLatestValueWithoutSummingAndShowScope() {
        val result = HealthMetricSummaryPolicy.summarize(
            records = listOf(
                record(
                    "steps",
                    1200.0,
                    "步",
                    "2026-09-03T23:00:00Z",
                    source = OnDeviceStepSnapshot.SOURCE,
                ),
                record(
                    "steps",
                    1800.0,
                    "步",
                    "2026-09-04T03:00:00Z",
                    source = OnDeviceStepSnapshot.SOURCE,
                ),
            ),
            now = now,
            zoneId = zone,
        )

        val steps = result.single { it.kind == "steps" }
        assertEquals(1800.0, steps.value, 0.0)
        assertEquals(HealthMetricDisplayMode.DEVICE_SINCE_BOOT, steps.displayMode)
    }

    @Test
    fun iqooDailyReportTakesPrecedenceOverDeviceStepSnapshot() {
        val result = HealthMetricSummaryPolicy.summarize(
            records = listOf(
                record(
                    "steps",
                    33336.0,
                    "步",
                    "2026-09-04T03:00:00Z",
                    source = OnDeviceStepSnapshot.SOURCE,
                ),
                record(
                    "steps",
                    8342.0,
                    "步",
                    "2026-09-04T03:30:00Z",
                    source = "manual:iqoo_watch_gt_e2b",
                ),
            ),
            now = now,
            zoneId = zone,
        )

        val steps = result.single { it.kind == "steps" }
        assertEquals(8342.0, steps.value, 0.0)
        assertEquals(HealthMetricDisplayMode.TODAY_TOTAL, steps.displayMode)
    }

    private fun record(
        kind: String,
        value: Double,
        unit: String,
        time: String,
        source: String = "test",
    ) = HealthRecordEntity(
        id = "$kind-$time-$value",
        kind = kind,
        value = value,
        unit = unit,
        observedAt = Instant.parse(time).toEpochMilli(),
        source = source,
    )
}
