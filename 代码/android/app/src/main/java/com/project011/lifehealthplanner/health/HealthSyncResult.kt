package com.project011.lifehealthplanner.health

import com.project011.lifehealthplanner.data.local.HealthRecordEntity
import kotlinx.coroutines.CancellationException

data class HealthSyncResult(
    val records: List<HealthRecordEntity>,
    val grantedTypeCount: Int,
    val requestedTypeCount: Int,
    val failedTypes: List<String> = emptyList(),
) {
    fun userMessage(): String {
        if (grantedTypeCount == 0) {
            return "尚未授予健康数据读取权限，请至少选择一类数据"
        }
        val permissionNote = if (grantedTypeCount < requestedTypeCount) {
            "；已授权 $grantedTypeCount/$requestedTypeCount 类"
        } else {
            ""
        }
        val failureNote = if (failedTypes.isNotEmpty()) {
            "；${failedTypes.distinct().joinToString("、")}暂时读取失败"
        } else {
            ""
        }
        return if (records.isEmpty()) {
            "Health Connect 最近 30 天没有可同步记录，请确认健康应用已向其中写入数据$permissionNote$failureNote"
        } else {
            "已同步 ${records.size} 条健康记录$permissionNote$failureNote"
        }
    }
}

internal suspend fun <T> collectGrantedHealthData(
    requestedTypes: List<T>,
    grantedTypes: Set<T>,
    label: (T) -> String,
    read: suspend (T) -> List<HealthRecordEntity>,
): HealthSyncResult {
    val records = mutableListOf<HealthRecordEntity>()
    val failures = mutableListOf<String>()
    requestedTypes.filter(grantedTypes::contains).forEach { type ->
        try {
            records += read(type)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            failures += label(type)
        }
    }
    return HealthSyncResult(
        records = records,
        grantedTypeCount = grantedTypes.size,
        requestedTypeCount = requestedTypes.size,
        failedTypes = failures,
    )
}
