package com.project011.lifehealthplanner.data

import com.project011.lifehealthplanner.data.local.AiConsentReceiptEntity
import com.project011.lifehealthplanner.data.local.BackupManifestEntity
import com.project011.lifehealthplanner.data.local.CalendarLinkEntity
import com.project011.lifehealthplanner.data.local.CheckInEntity
import com.project011.lifehealthplanner.data.local.ConstraintEntity
import com.project011.lifehealthplanner.data.local.HealthRecordEntity
import com.project011.lifehealthplanner.data.local.LifeGoalEntity
import com.project011.lifehealthplanner.data.local.MedicationEntity
import com.project011.lifehealthplanner.data.local.PlanEntity
import com.project011.lifehealthplanner.data.local.PlanItemEntity
import com.project011.lifehealthplanner.data.local.RiskAlertEntity
import com.project011.lifehealthplanner.data.local.UserProfileEntity

data class BackupPayload(
    val version: Int = 1,
    val exportedAt: Long,
    val profile: UserProfileEntity?,
    val healthRecords: List<HealthRecordEntity>,
    val medications: List<MedicationEntity>,
    val goals: List<LifeGoalEntity>,
    val constraints: List<ConstraintEntity>,
    val plans: List<PlanEntity>,
    val planItems: List<PlanItemEntity>,
    val checkIns: List<CheckInEntity>,
    val riskAlerts: List<RiskAlertEntity>,
    val calendarLinks: List<CalendarLinkEntity>,
    val consentReceipts: List<AiConsentReceiptEntity>,
    val backupManifests: List<BackupManifestEntity>,
)
