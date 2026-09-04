package com.project011.lifehealthplanner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfile(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: UserProfileEntity)

    @Query("DELETE FROM user_profile")
    suspend fun clearProfile()

    @Query("SELECT * FROM health_records ORDER BY observedAt DESC")
    fun observeHealthRecords(): Flow<List<HealthRecordEntity>>

    @Query("SELECT * FROM health_records ORDER BY observedAt DESC")
    suspend fun getHealthRecords(): List<HealthRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHealthRecords(records: List<HealthRecordEntity>)

    @Query("SELECT * FROM medications ORDER BY createdAt DESC")
    fun observeMedications(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications ORDER BY createdAt DESC")
    suspend fun getMedications(): List<MedicationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMedication(medication: MedicationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMedications(medications: List<MedicationEntity>)

    @Query("SELECT * FROM life_goals WHERE status = 'active' ORDER BY priority DESC, createdAt")
    fun observeActiveGoals(): Flow<List<LifeGoalEntity>>

    @Query("SELECT * FROM life_goals ORDER BY createdAt")
    suspend fun getGoals(): List<LifeGoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGoal(goal: LifeGoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGoals(goals: List<LifeGoalEntity>)

    @Query("SELECT * FROM constraints ORDER BY createdAt")
    suspend fun getConstraints(): List<ConstraintEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConstraints(constraints: List<ConstraintEntity>)

    @Query("SELECT * FROM plans ORDER BY periodStart DESC")
    fun observePlans(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans ORDER BY createdAt")
    suspend fun getPlans(): List<PlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlan(plan: PlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlans(plans: List<PlanEntity>)

    @Query("SELECT * FROM plan_items ORDER BY startAt")
    fun observeAllPlanItems(): Flow<List<PlanItemEntity>>

    @Query("SELECT * FROM plan_items WHERE startAt >= :start AND startAt < :end ORDER BY startAt")
    fun observePlanItemsBetween(start: Long, end: Long): Flow<List<PlanItemEntity>>

    @Query("SELECT * FROM plan_items ORDER BY startAt")
    suspend fun getPlanItems(): List<PlanItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlanItems(items: List<PlanItemEntity>)

    @Query("UPDATE plan_items SET status = :status WHERE id = :itemId")
    suspend fun updatePlanItemStatus(itemId: String, status: String)

    @Query("SELECT * FROM check_ins ORDER BY createdAt DESC")
    suspend fun getCheckIns(): List<CheckInEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCheckIn(checkIn: CheckInEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCheckIns(checkIns: List<CheckInEntity>)

    @Query("SELECT * FROM risk_alerts ORDER BY createdAt DESC")
    suspend fun getRiskAlerts(): List<RiskAlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRiskAlerts(alerts: List<RiskAlertEntity>)

    @Query("SELECT * FROM calendar_links")
    suspend fun getCalendarLinks(): List<CalendarLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCalendarLink(link: CalendarLinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCalendarLinks(links: List<CalendarLinkEntity>)

    @Query("SELECT * FROM ai_consent_receipts ORDER BY createdAt")
    suspend fun getConsentReceipts(): List<AiConsentReceiptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConsentReceipt(receipt: AiConsentReceiptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConsentReceipts(receipts: List<AiConsentReceiptEntity>)

    @Query("SELECT * FROM backup_manifests ORDER BY createdAt DESC")
    suspend fun getBackupManifests(): List<BackupManifestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBackupManifest(manifest: BackupManifestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBackupManifests(manifests: List<BackupManifestEntity>)

    @Query("DELETE FROM check_ins")
    suspend fun clearCheckIns()

    @Query("DELETE FROM plan_items")
    suspend fun clearPlanItems()

    @Query("DELETE FROM plans")
    suspend fun clearPlans()

    @Query("DELETE FROM life_goals")
    suspend fun clearGoals()

    @Query("DELETE FROM medications")
    suspend fun clearMedications()

    @Query("DELETE FROM health_records")
    suspend fun clearHealthRecords()

    @Query("DELETE FROM constraints")
    suspend fun clearConstraints()

    @Query("DELETE FROM risk_alerts")
    suspend fun clearRiskAlerts()

    @Query("DELETE FROM calendar_links")
    suspend fun clearCalendarLinks()

    @Query("DELETE FROM ai_consent_receipts")
    suspend fun clearConsentReceipts()

    @Query("DELETE FROM backup_manifests")
    suspend fun clearBackupManifests()
}
