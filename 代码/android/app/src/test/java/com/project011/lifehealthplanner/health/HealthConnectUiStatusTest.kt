package com.project011.lifehealthplanner.health

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectUiStatusTest {
    @Test
    fun unavailableAndUpdateRequiredHaveDifferentMessages() {
        val unavailable = status(HealthConnectAvailability.UNAVAILABLE)
        val updateRequired = status(HealthConnectAvailability.UPDATE_REQUIRED)

        assertTrue(unavailable.statusMessage().contains("未提供或已停用"))
        assertTrue(updateRequired.statusMessage().contains("安装或更新"))
        assertFalse(unavailable.isAvailable)
        assertTrue(updateRequired.needsUpdate)
        assertNull(unavailable.stepGuidance())
    }

    @Test
    fun availableWithoutImportedRecordsExplainsWhatToCheck() {
        val status = status(HealthConnectAvailability.AVAILABLE, grantedPermissions = 3)

        assertTrue(status.isAvailable)
        assertTrue(status.statusMessage().contains("尚未读到数据"))
        assertTrue(status.statusMessage().contains("数据来源"))
        assertTrue(status.statusMessage().contains("3/7"))
    }

    @Test
    fun availableWithImportedRecordsReportsSuccess() {
        val status = status(
            availability = HealthConnectAvailability.AVAILABLE,
            hasSyncedRecords = true,
            grantedPermissions = 7,
        )

        assertTrue(status.statusMessage().contains("本机保存有已同步记录"))
    }

    @Test
    fun revokedPermissionsAreNotHiddenByOldRecords() {
        val status = status(
            availability = HealthConnectAvailability.AVAILABLE,
            hasSyncedRecords = true,
            grantedPermissions = 0,
        )

        assertTrue(status.statusMessage().contains("尚未授权任何"))
        assertFalse(status.statusMessage().contains("已同步记录"))
    }

    @Test
    fun onDeviceStepCapabilityDoesNotDependOnVivoHealth() {
        val supported = status(
            availability = HealthConnectAvailability.AVAILABLE,
            onDeviceSteps = true,
        )
        val unsupported = status(
            availability = HealthConnectAvailability.AVAILABLE,
            onDeviceSteps = false,
        )

        assertTrue(supported.stepGuidance().orEmpty().contains("无需依赖 vivo 健康"))
        assertTrue(unsupported.stepGuidance().orEmpty().contains("兼容的健康应用写入"))
    }

    @Test
    fun deviceStepFallbackIsShownWhenHealthConnectIsUnavailable() {
        val status = status(
            availability = HealthConnectAvailability.UNAVAILABLE,
            onDeviceSteps = true,
        )

        assertTrue(status.statusMessage().contains("读取本机步数"))
        assertTrue(status.stepGuidance().orEmpty().contains("无需依赖 vivo 健康"))
    }

    @Test
    fun grantedPermissionsWithoutRecordsExplainsHealthConnectDataSource() {
        val status = status(
            availability = HealthConnectAvailability.AVAILABLE,
            grantedPermissions = 1,
        )

        assertTrue(status.noDataGuidance().orEmpty().contains("写入 Health Connect"))
    }

    @Test
    fun noDataGuidanceIsHiddenBeforePermissionCheckOrAfterSync() {
        assertNull(status(HealthConnectAvailability.AVAILABLE).noDataGuidance())
        assertNull(status(HealthConnectAvailability.AVAILABLE, grantedPermissions = 1, hasSyncedRecords = true).noDataGuidance())
        assertNull(status(HealthConnectAvailability.AVAILABLE, grantedPermissions = 0).noDataGuidance())
    }

    private fun status(
        availability: HealthConnectAvailability,
        hasSyncedRecords: Boolean = false,
        onDeviceSteps: Boolean = false,
        grantedPermissions: Int? = null,
    ) = HealthConnectUiStatus(
        availability = availability,
        hasSyncedRecords = hasSyncedRecords,
        onDeviceStepCountingAvailable = onDeviceSteps,
        grantedPermissionCount = grantedPermissions,
        requestedPermissionCount = 7,
    )
}
