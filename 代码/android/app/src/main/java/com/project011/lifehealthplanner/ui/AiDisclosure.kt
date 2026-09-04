package com.project011.lifehealthplanner.ui

internal fun aiFieldSummary(state: AppUiState): String {
    val fields = mutableListOf("年龄段", "地区与时区", "执行反馈摘要")
    val profile = state.profile
    if (profile?.heightCm != null || profile?.weightKg != null) fields += "身体指标"
    val hasProfileHealth = profile?.let { it.conditionsJson != "[]" || it.allergiesJson != "[]" } == true
    if (hasProfileHealth || state.medications.isNotEmpty()) {
        fields += "健康约束、过敏与用药"
    }
    if (profile?.workSchedule?.isNotBlank() == true) fields += "固定工作安排"
    if (profile?.preferencesJson != "[]" || profile?.sleepHours != null) fields += "生活偏好与睡眠目标"
    if (state.goals.isNotEmpty()) fields += "目标"
    if (state.healthRecords.isNotEmpty()) fields += "近期健康指标"
    if (profile?.weeklyBudget != null) fields += "周预算"
    fields += "匿名忙碌时段"
    return fields.distinct().joinToString("、")
}
