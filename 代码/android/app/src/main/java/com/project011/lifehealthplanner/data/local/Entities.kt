package com.project011.lifehealthplanner.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val displayName: String = "",
    val birthDate: String = "",
    val ageBand: String = "18-29",
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val region: String = "",
    val emergencyNumber: String = "",
    val conditionsJson: String = "[]",
    val allergiesJson: String = "[]",
    val preferencesJson: String = "[]",
    val workSchedule: String = "",
    val sleepHours: Double = 8.0,
    val weeklyBudget: Double? = null,
    val timezone: String = "",
    val appLockEnabled: Boolean = true,
    val onboardingComplete: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "health_records", indices = [Index("kind"), Index("observedAt")])
data class HealthRecordEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val value: Double,
    val unit: String,
    val observedAt: Long,
    val source: String,
)

@Entity(tableName = "medications", indices = [Index("activeIngredient")])
data class MedicationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val activeIngredient: String,
    val dose: String,
    val schedule: String,
    val contraindications: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "life_goals", indices = [Index("domain"), Index("status")])
data class LifeGoalEntity(
    @PrimaryKey val id: String,
    val domain: String,
    val title: String,
    val target: String,
    val priority: Int,
    val targetDate: Long? = null,
    val status: String = "active",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "constraints", indices = [Index("kind")])
data class ConstraintEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val details: String,
    val isHard: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "plans", indices = [Index("periodStart"), Index("status")])
data class PlanEntity(
    @PrimaryKey val id: String,
    val title: String,
    val periodStart: Long,
    val periodEnd: Long,
    val status: String,
    val riskLevel: String,
    val riskMessage: String,
    val summary: String,
    val rationaleJson: String = "[]",
    val reviewQuestionsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "plan_items",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("planId"), Index("startAt"), Index("status")],
)
data class PlanItemEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val domain: String,
    val title: String,
    val description: String,
    val startAt: Long,
    val endAt: Long,
    val priority: Int,
    val energy: String,
    val estimatedCost: Double,
    val goalIdsJson: String = "[]",
    val reminderMinutesJson: String = "[]",
    val safetyTagsJson: String = "[]",
    val status: String = "planned",
)

@Entity(
    tableName = "check_ins",
    indices = [Index("planItemId"), Index("createdAt")],
)
data class CheckInEntity(
    @PrimaryKey val id: String,
    val planItemId: String,
    val status: String,
    val difficulty: Int,
    val energy: Int,
    val note: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "risk_alerts", indices = [Index("level"), Index("createdAt")])
data class RiskAlertEntity(
    @PrimaryKey val id: String,
    val level: String,
    val message: String,
    val source: String,
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
)

@Entity(tableName = "calendar_links")
data class CalendarLinkEntity(
    @PrimaryKey val planItemId: String,
    val eventId: Long,
    val calendarId: Long,
)

@Entity(tableName = "ai_consent_receipts", indices = [Index("createdAt")])
data class AiConsentReceiptEntity(
    @PrimaryKey val id: String,
    val endpoint: String,
    val fieldSummaryJson: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "backup_manifests", indices = [Index("createdAt")])
data class BackupManifestEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val sha256: String,
    val serverReceiptId: String,
    val status: String,
)
