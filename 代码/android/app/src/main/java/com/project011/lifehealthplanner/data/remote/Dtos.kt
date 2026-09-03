package com.project011.lifehealthplanner.data.remote

import com.google.gson.annotations.SerializedName

data class StatusDto(
    val version: String,
    @SerializedName("ai_configured") val aiConfigured: Boolean,
    @SerializedName("planning_model") val planningModel: String,
    @SerializedName("economy_model") val economyModel: String,
    @SerializedName("server_time") val serverTime: String,
)

data class PairCompleteRequestDto(
    val code: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_name") val deviceName: String,
)

data class PairCompleteResponseDto(
    @SerializedName("device_id") val deviceId: String,
    val token: String,
    @SerializedName("server_time") val serverTime: String,
)

data class GoalSnapshotDto(
    val id: String,
    val domain: String,
    val title: String,
    val target: String,
    val priority: Int,
    @SerializedName("target_date") val targetDate: String?,
)

data class MetricSnapshotDto(
    val kind: String,
    val value: Double,
    val unit: String,
    @SerializedName("measured_at") val measuredAt: String,
)

data class BusyBlockDto(
    @SerializedName("start_at") val startAt: String,
    @SerializedName("end_at") val endAt: String,
    val label: String = "忙碌",
)

data class AiContextSnapshotDto(
    @SerializedName("age_band") val ageBand: String,
    val timezone: String,
    val region: String,
    @SerializedName("health_constraints") val healthConstraints: List<String>,
    @SerializedName("schedule_constraints") val scheduleConstraints: List<String>,
    val preferences: List<String>,
    val goals: List<GoalSnapshotDto>,
    val metrics: List<MetricSnapshotDto>,
    @SerializedName("busy_blocks") val busyBlocks: List<BusyBlockDto>,
    @SerializedName("weekly_budget") val weeklyBudget: Double?,
    @SerializedName("recent_feedback") val recentFeedback: List<String>,
    @SerializedName("emergency_number") val emergencyNumber: String,
)

data class PlanRequestDto(
    val context: AiContextSnapshotDto,
    @SerializedName("period_start") val periodStart: String,
    @SerializedName("period_end") val periodEnd: String,
    val focus: String,
)

data class PlanItemDto(
    val id: String,
    val domain: String,
    val title: String,
    val description: String,
    @SerializedName("start_at") val startAt: String,
    @SerializedName("end_at") val endAt: String,
    val priority: Int,
    val energy: String,
    @SerializedName("estimated_cost") val estimatedCost: Double,
    @SerializedName("goal_ids") val goalIds: List<String>,
    @SerializedName("reminder_minutes") val reminderMinutes: List<Int>,
    @SerializedName("safety_tags") val safetyTags: List<String>,
)

data class PlanDraftDto(
    val title: String,
    val summary: String,
    val rationale: List<String>,
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("risk_message") val riskMessage: String,
    val items: List<PlanItemDto>,
    @SerializedName("review_questions") val reviewQuestions: List<String>,
)

data class ChatRequestDto(
    val context: AiContextSnapshotDto,
    val message: String,
    @SerializedName("local_summary") val localSummary: String,
)

data class SuggestedActionDto(
    val title: String,
    val domain: String,
    val details: String,
    @SerializedName("requires_plan_confirmation") val requiresPlanConfirmation: Boolean,
)

data class ChatReplyDto(
    val reply: String,
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("risk_message") val riskMessage: String,
    @SerializedName("suggested_actions") val suggestedActions: List<SuggestedActionDto>,
)

data class BackupEnvelopeDto(
    val version: Int = 1,
    @SerializedName("created_at") val createdAt: String,
    val salt: String,
    val nonce: String,
    val ciphertext: String,
    val sha256: String,
)

data class BackupReceiptDto(
    @SerializedName("backup_id") val backupId: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("byte_count") val byteCount: Int,
    val sha256: String,
)
