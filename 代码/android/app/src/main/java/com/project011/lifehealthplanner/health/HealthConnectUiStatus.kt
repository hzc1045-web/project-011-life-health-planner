package com.project011.lifehealthplanner.health

enum class HealthConnectAvailability {
    AVAILABLE,
    UPDATE_REQUIRED,
    UNAVAILABLE,
}

data class HealthConnectUiStatus(
    val availability: HealthConnectAvailability,
    val hasSyncedRecords: Boolean,
    val onDeviceStepCountingAvailable: Boolean,
    val onDeviceStepPermissionGranted: Boolean = false,
    val grantedPermissionCount: Int? = null,
    val requestedPermissionCount: Int = 0,
) {
    val isAvailable: Boolean = availability == HealthConnectAvailability.AVAILABLE
    val needsUpdate: Boolean = availability == HealthConnectAvailability.UPDATE_REQUIRED

    fun statusMessage(): String = when (availability) {
        HealthConnectAvailability.AVAILABLE -> availableMessage()
        HealthConnectAvailability.UPDATE_REQUIRED ->
            if (onDeviceStepCountingAvailable) {
                "Health Connect 需要安装或更新；可先读取本机步数或使用手工记录。"
            } else {
                "Health Connect 需要安装或更新，可继续使用手工记录。"
            }
        HealthConnectAvailability.UNAVAILABLE ->
            if (onDeviceStepCountingAvailable) {
                "此设备未提供或已停用 Health Connect；可先读取本机步数或使用手工记录。"
            } else {
                "此设备未提供或已停用 Health Connect，可继续使用手工记录。"
            }
    }

    private fun availableMessage(): String = when (val granted = grantedPermissionCount) {
        null -> "Health Connect 可用，正在检查授权状态。"
        0 -> "Health Connect 可用，但尚未授权任何健康数据。点击“同步”后至少选择一类数据。"
        else -> {
            val permissionSummary = if (requestedPermissionCount > 0) {
                "已授权 $granted/$requestedPermissionCount 类"
            } else {
                "已授权 $granted 类"
            }
            if (hasSyncedRecords) {
                "Health Connect 可用；$permissionSummary，本机保存有已同步记录。"
            } else {
                "Health Connect 可用；$permissionSummary，但最近 30 天尚未读到数据。请检查数据来源。"
            }
        }
    }

    fun stepGuidance(): String? {
        if (onDeviceStepCountingAvailable) {
            return if (onDeviceStepPermissionGranted) {
                "本机计步器已授权；读取的是自最近一次开机以来的累计步数，不是 vivo/iQOO 健康历史数据。"
            } else {
                "本机支持硬件计步。授予“身体活动”权限后即可读取，无需依赖 vivo 健康。"
            }
        }
        if (!isAvailable) return null
        return "本机尚不支持 Health Connect 设备计步；步数需要由兼容的健康应用写入。"
    }

    fun noDataGuidance(): String? {
        if (!isAvailable || grantedPermissionCount == null || grantedPermissionCount == 0 || hasSyncedRecords) {
            return null
        }
        return "Health Connect 只读取其中已有的数据；如果 iQOO/vivo 健康没有写入 Health Connect，这里不会自动出现记录。请在“管理 Health Connect”中检查数据来源，或先使用手工记录。"
    }
}
