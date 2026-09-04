package com.project011.lifehealthplanner.health

import com.project011.lifehealthplanner.data.local.HealthRecordEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HealthSyncResultTest {
    @Test
    fun emptyHealthConnectPageTokenStopsPagination() {
        assertFalse(hasNextPage(null))
        assertFalse(hasNextPage(""))
        assertFalse(hasNextPage("   "))
        assertTrue(hasNextPage("next-page"))
    }

    @Test
    fun partialPermissionStillReadsGrantedTypes() = runTest {
        val result = collectGrantedHealthData(
            requestedTypes = listOf("steps", "sleep"),
            grantedTypes = setOf("steps"),
            label = { it },
        ) { type ->
            if (type == "steps") listOf(record("steps")) else emptyList()
        }

        assertEquals(1, result.records.size)
        assertEquals(1, result.grantedTypeCount)
        assertTrue(result.userMessage().contains("已授权 1/2 类"))
    }

    @Test
    fun oneFailedTypeDoesNotDiscardSuccessfulRecords() = runTest {
        val result = collectGrantedHealthData(
            requestedTypes = listOf("steps", "sleep"),
            grantedTypes = setOf("steps", "sleep"),
            label = { if (it == "sleep") "睡眠" else it },
        ) { type ->
            if (type == "sleep") error("temporary failure")
            listOf(record(type))
        }

        assertEquals(listOf("steps"), result.records.map { it.kind })
        assertEquals(listOf("睡眠"), result.failedTypes)
        assertTrue(result.userMessage().contains("睡眠"))
    }

    @Test
    fun healthConnectIdentityIsStableAndTypeSpecific() {
        val time = Instant.parse("2026-09-01T08:00:00Z")
        val first = HealthRecordIdentity.create("steps", "record-1", "source", time)
        val repeated = HealthRecordIdentity.create("steps", "record-1", "source", time)
        val differentType = HealthRecordIdentity.create("weight", "record-1", "source", time)

        assertEquals(first, repeated)
        assertNotEquals(first, differentType)
    }

    @Test
    fun healthConnectIdentitySurvivesSourceTimeCorrection() {
        val first = HealthRecordIdentity.create(
            "steps",
            "record-1",
            "source",
            Instant.parse("2026-09-01T08:00:00Z"),
        )
        val corrected = HealthRecordIdentity.create(
            "steps",
            "record-1",
            "source",
            Instant.parse("2026-09-01T09:00:00Z"),
        )
        val otherSource = HealthRecordIdentity.create(
            "steps",
            "record-1",
            "other-source",
            Instant.parse("2026-09-01T09:00:00Z"),
        )

        assertEquals(first, corrected)
        assertNotEquals(first, otherSource)
    }

    @Test
    fun healthConnectIdentityUsesTimeOnlyWhenExternalIdIsBlank() {
        val first = HealthRecordIdentity.create(
            "steps",
            " ",
            "source",
            Instant.parse("2026-09-01T08:00:00Z"),
        )
        val later = HealthRecordIdentity.create(
            "steps",
            "",
            "source",
            Instant.parse("2026-09-01T09:00:00Z"),
        )

        assertNotEquals(first, later)
    }

    private fun record(kind: String) = HealthRecordEntity(
        id = kind,
        kind = kind,
        value = 1.0,
        unit = "unit",
        observedAt = 1L,
        source = "test",
    )
}
