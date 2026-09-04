package com.project011.lifehealthplanner.data

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.project011.lifehealthplanner.backup.BackupCrypto
import com.project011.lifehealthplanner.calendar.CalendarManager
import com.project011.lifehealthplanner.data.local.AiConsentReceiptEntity
import com.project011.lifehealthplanner.data.local.AppDao
import com.project011.lifehealthplanner.data.local.AppDatabase
import com.project011.lifehealthplanner.data.local.BackupManifestEntity
import com.project011.lifehealthplanner.data.local.CalendarLinkEntity
import com.project011.lifehealthplanner.data.local.CheckInEntity
import com.project011.lifehealthplanner.data.local.HealthRecordEntity
import com.project011.lifehealthplanner.data.local.LifeGoalEntity
import com.project011.lifehealthplanner.data.local.MedicationEntity
import com.project011.lifehealthplanner.data.local.PlanEntity
import com.project011.lifehealthplanner.data.local.PlanItemEntity
import com.project011.lifehealthplanner.data.local.UserProfileEntity
import com.project011.lifehealthplanner.data.remote.AiContextSnapshotDto
import com.project011.lifehealthplanner.data.remote.AiContextMinimizer
import com.project011.lifehealthplanner.data.remote.BackupEnvelopeDto
import com.project011.lifehealthplanner.data.remote.BusyBlockDto
import com.project011.lifehealthplanner.data.remote.ChatReplyDto
import com.project011.lifehealthplanner.data.remote.ChatRequestDto
import com.project011.lifehealthplanner.data.remote.CompanionClient
import com.project011.lifehealthplanner.data.remote.PairCompleteRequestDto
import com.project011.lifehealthplanner.data.remote.PlanDraftDto
import com.project011.lifehealthplanner.data.remote.PlanItemDto
import com.project011.lifehealthplanner.data.remote.PlanRequestDto
import com.project011.lifehealthplanner.data.remote.StatusDto
import com.project011.lifehealthplanner.domain.PlanValidationResult
import com.project011.lifehealthplanner.domain.PlanValidator
import com.project011.lifehealthplanner.domain.FeedbackAdjuster
import com.project011.lifehealthplanner.domain.OfflinePlanSchedule
import com.project011.lifehealthplanner.domain.RiskDetector
import com.project011.lifehealthplanner.domain.RiskLevel
import com.project011.lifehealthplanner.health.HealthConnectManager
import com.project011.lifehealthplanner.health.OnDeviceStepSnapshot
import com.project011.lifehealthplanner.notifications.ReminderPolicy
import com.project011.lifehealthplanner.notifications.ReminderWorker
import com.project011.lifehealthplanner.pairing.PairingLinkParser
import com.project011.lifehealthplanner.security.SecurePreferences
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

class AppRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val securePreferences: SecurePreferences,
    private val healthConnect: HealthConnectManager,
    private val calendar: CalendarManager,
    private val gson: Gson = Gson(),
) {
    private val dao: AppDao = database.dao()

    val profile: Flow<UserProfileEntity?> = dao.observeProfile()
    val goals: Flow<List<LifeGoalEntity>> = dao.observeActiveGoals()
    val healthRecords: Flow<List<HealthRecordEntity>> = dao.observeHealthRecords()
    val medications: Flow<List<MedicationEntity>> = dao.observeMedications()
    val plans: Flow<List<PlanEntity>> = dao.observePlans()
    val planItems: Flow<List<PlanItemEntity>> = dao.observeAllPlanItems()

    suspend fun saveProfile(profile: UserProfileEntity) = dao.saveProfile(profile)

    suspend fun saveGoal(goal: LifeGoalEntity) = dao.saveGoal(goal)

    suspend fun saveMedication(medication: MedicationEntity) = dao.saveMedication(medication)

    suspend fun saveManualHealth(kind: String, value: Double, unit: String) {
        dao.saveHealthRecords(
            listOf(
                HealthRecordEntity(
                    id = "manual-${UUID.randomUUID()}",
                    kind = kind,
                    value = value,
                    unit = unit,
                    observedAt = System.currentTimeMillis(),
                    source = "manual",
                ),
            ),
        )
    }

    suspend fun syncHealthConnect(): com.project011.lifehealthplanner.health.HealthSyncResult {
        val result = healthConnect.readRecent()
        if (result.records.isNotEmpty()) dao.saveHealthRecords(result.records)
        return result
    }

    /**
     * Reads the phone's hardware counter as a clearly labelled fallback when
     * Health Connect (or vivo Health's data bridge) is unavailable. The
     * counter is cumulative since the latest reboot, so one stable row is
     * replaced on every read instead of summing snapshots together.
     */
    suspend fun readOnDeviceSteps(): OnDeviceStepSnapshot {
        val snapshot = healthConnect.readOnDeviceSteps()
        dao.saveHealthRecords(
            listOf(
                HealthRecordEntity(
                    id = ON_DEVICE_STEP_RECORD_ID,
                    kind = "steps",
                    value = snapshot.stepsSinceBoot.toDouble(),
                    unit = "步",
                    observedAt = snapshot.observedAtEpochMillis,
                    source = snapshot.source,
                ),
            ),
        )
        return snapshot
    }

    fun healthPermissions() = healthConnect.permissions

    fun healthPermissionContract() = healthConnect.permissionContract()

    fun healthSdkStatus() = healthConnect.sdkStatus()

    suspend fun healthGrantedPermissionCount() = healthConnect.grantedPermissionCount()

    fun healthOnDeviceStepCountingAvailable() = healthConnect.onDeviceStepCountingAvailable()

    fun healthOnDeviceStepPermissionGranted() = healthConnect.onDeviceStepPermissionGranted()

    fun openHealthConnectManagement() = healthConnect.openManagement()

    suspend fun pair(serverUrl: String, code: String, deviceName: String) {
        val normalizedServerUrl = PairingLinkParser.normalizeServerUrl(serverUrl)
            ?: error("电脑地址无效，只能连接 HTTPS 的 Tailscale（ts.net）地址")
        val deviceId = securePreferences.getOrCreateDeviceId()
        val response = CompanionClient.unauthenticated(normalizedServerUrl).pair(
            PairCompleteRequestDto(code, deviceId, deviceName),
        )
        securePreferences.saveCompanion(normalizedServerUrl, response.token)
    }

    fun isPaired(): Boolean = securePreferences.credentials() != null

    fun companionServer(): String = securePreferences.credentials()?.serverUrl.orEmpty()

    suspend fun companionStatus(): StatusDto {
        val credentials = securePreferences.credentials() ?: error("尚未配对电脑")
        return CompanionClient.authenticated(credentials).status()
    }

    fun clearPairing() = securePreferences.clearCompanion()

    suspend fun requestAiPlan(
        focus: String,
        start: Instant,
        end: Instant,
        expectedProviderId: String,
    ): Pair<PlanDraftDto, PlanValidationResult> {
        val userRisk = assessUserRisk(focus)
        require(userRisk.level != RiskLevel.URGENT) {
            "${userRisk.message}，已停止发送健康数据并暂停普通计划"
        }
        val credentials = securePreferences.credentials() ?: error("尚未配对电脑")
        val client = CompanionClient.authenticated(credentials)
        val status = client.status()
        require(expectedProviderId.isNotBlank() && status.activeProvider == expectedProviderId) {
            "AI 提供商已变化，请刷新状态并重新确认数据接收方"
        }
        require(status.aiConfigured) { "${status.providerDisplayName} API 密钥尚未配置" }
        val contextSnapshot = buildAiContext(start, end)
        recordConsent("/ai/plan@${status.activeProvider}", contextSnapshot)
        val request = PlanRequestDto(
            context = contextSnapshot,
            periodStart = start.toString(),
            periodEnd = end.toString(),
            focus = focus,
        )
        val draft = client.createPlan(expectedProviderId, request)
        val validation = PlanValidator.validate(
            draft,
            start,
            end,
            contextSnapshot.busyBlocks,
            contextSnapshot.weeklyBudget,
            contextSnapshot.healthConstraints,
        )
        return draft to validation.withUserRisk(assessUserRisk(focus))
    }

    suspend fun requestChat(
        message: String,
        localSummary: String,
        expectedProviderId: String,
    ): ChatReplyDto {
        val credentials = securePreferences.credentials() ?: error("尚未配对电脑")
        val client = CompanionClient.authenticated(credentials)
        val status = client.status()
        require(expectedProviderId.isNotBlank() && status.activeProvider == expectedProviderId) {
            "AI 提供商已变化，请刷新状态并重新确认数据接收方"
        }
        require(status.aiConfigured) { "${status.providerDisplayName} API 密钥尚未配置" }
        val start = Instant.now()
        val contextSnapshot = buildAiContext(start, start.plus(7, ChronoUnit.DAYS))
        recordConsent("/ai/chat@${status.activeProvider}", contextSnapshot)
        return client.chat(
            expectedProviderId,
            ChatRequestDto(contextSnapshot, message, localSummary),
        )
    }

    suspend fun createOfflinePlan(start: Instant, end: Instant): PlanDraftDto {
        val activeGoals = dao.getGoals().filter { it.status == "active" }.sortedByDescending { it.priority }
        val templates = buildList {
            add(Triple("health", "轻松步行", 30L))
            activeGoals.take(2).forEach { goal -> add(Triple(goal.domain, goal.title, 45L)) }
            add(Triple("learning", "本周复盘", 30L))
        }
        val slots = OfflinePlanSchedule.create(
            periodStart = start,
            periodEnd = end,
            durationsMinutes = templates.map { it.third },
            zoneId = ZoneId.systemDefault(),
        )
        val items = slots.map { slot ->
            val (domain, title, _) = templates[slot.itemIndex]
            PlanItemDto(
                id = "offline-${UUID.randomUUID()}",
                domain = domain,
                title = title,
                description = "离线基础计划，可在确认前调整。",
                startAt = slot.start.toString(),
                endAt = slot.end.toString(),
                priority = if (domain == "health") 5 else 4,
                energy = "medium",
                estimatedCost = 0.0,
                goalIds = activeGoals.filter { it.title == title }.map { it.id },
                reminderMinutes = listOf(15),
                safetyTags = emptyList(),
            )
        }
        return PlanDraftDto(
            title = "离线基础计划",
            summary = "电脑或 AI 不可用时生成的本地基础安排。",
            rationale = listOf("优先保留健康活动", "按当前高优先级目标安排短时段"),
            riskLevel = "normal",
            riskMessage = "",
            items = items,
            reviewQuestions = listOf("这些安排是否符合你本周的精力？"),
        )
    }

    suspend fun validateDraft(
        draft: PlanDraftDto,
        start: Instant,
        end: Instant,
    ): PlanValidationResult {
        val profile = dao.getProfile()
        val busy = calendar.readBusyBlocks(start, end)
        val validation = PlanValidator.validate(
            draft,
            start,
            end,
            busy,
            profile?.weeklyBudget,
            profile?.let { parseStrings(it.conditionsJson) }.orEmpty(),
        )
        return validation.withUserRisk(assessUserRisk(""))
    }

    suspend fun confirmPlan(draft: PlanDraftDto, start: Instant, end: Instant): PlanConfirmationResult {
        val validation = validateDraft(draft, start, end)
        require(validation.isValid) { validation.errors.joinToString("；") }
        val planId = UUID.randomUUID().toString()
        val plan = PlanEntity(
            id = planId,
            title = draft.title,
            periodStart = start.toEpochMilli(),
            periodEnd = end.toEpochMilli(),
            status = "confirmed",
            riskLevel = draft.riskLevel,
            riskMessage = draft.riskMessage,
            summary = draft.summary,
            rationaleJson = gson.toJson(draft.rationale),
            reviewQuestionsJson = gson.toJson(draft.reviewQuestions),
        )
        val items = draft.items.map { it.toEntity(planId) }
        database.withTransaction {
            dao.savePlan(plan)
            dao.savePlanItems(items)
        }
        val integrations = reconcilePlanIntegrations(items)
        return PlanConfirmationResult(
            planId = planId,
            itemCount = items.size,
            calendarCount = integrations.calendarCount,
            reminderCount = integrations.reminderCount,
            calendarPendingCount = integrations.calendarPendingCount,
            reminderFailureCount = integrations.reminderFailureCount,
        )
    }

    suspend fun checkIn(itemId: String, status: String, difficulty: Int, energy: Int, note: String) {
        database.withTransaction {
            dao.updatePlanItemStatus(itemId, status)
            dao.saveCheckIn(
                CheckInEntity(
                    id = UUID.randomUUID().toString(),
                    planItemId = itemId,
                    status = status,
                    difficulty = difficulty.coerceIn(1, 5),
                    energy = energy.coerceIn(1, 5),
                    note = note,
                ),
            )
        }
        if (ReminderPolicy.shouldCancel(status)) {
            ReminderWorker.cancel(context, itemId)
        }
    }

    suspend fun uploadEncryptedBackup(password: CharArray): String {
        val credentials = securePreferences.credentials() ?: error("尚未配对电脑")
        val envelope = BackupCrypto.encrypt(gson.toJson(exportPayload()), password)
        val receipt = CompanionClient.authenticated(credentials)
            .uploadBackup(credentials.deviceId, envelope)
        dao.saveBackupManifest(
            BackupManifestEntity(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                sha256 = receipt.sha256,
                serverReceiptId = receipt.backupId,
                status = "uploaded",
            ),
        )
        return receipt.backupId
    }

    suspend fun exportDataJson(): String = gson.toJson(
        exportPayload().copy(
            calendarLinks = emptyList(),
            consentReceipts = emptyList(),
            backupManifests = emptyList(),
        ),
    )

    suspend fun restoreLatestBackup(password: CharArray): PlanIntegrationResult {
        val credentials = securePreferences.credentials() ?: error("尚未配对电脑")
        val envelope = CompanionClient.authenticated(credentials).latestBackup(credentials.deviceId)
        val payload = gson.fromJson(
            BackupCrypto.decrypt(envelope, password),
            BackupPayload::class.java,
        )
        require(payload.version == 1) { "不支持的备份版本" }
        val oldPlanItemIds = dao.getPlanItems().map { it.id }
        val oldCalendarLinks = dao.getCalendarLinks()
        database.withTransaction {
            dao.clearCheckIns()
            dao.clearPlanItems()
            dao.clearPlans()
            dao.clearGoals()
            dao.clearMedications()
            dao.clearHealthRecords()
            dao.clearConstraints()
            dao.clearRiskAlerts()
            dao.clearCalendarLinks()
            dao.clearConsentReceipts()
            dao.clearBackupManifests()
            // A backup without a profile represents an intentionally empty
            // profile; clear the existing row before conditionally restoring it.
            dao.clearProfile()
            payload.profile?.let { dao.saveProfile(it) }
            // A hardware step snapshot is tied to the old phone's boot cycle;
            // restoring it on a new device would present stale steps as current.
            dao.saveHealthRecords(
                payload.healthRecords.filterNot { it.source == OnDeviceStepSnapshot.SOURCE },
            )
            dao.saveMedications(payload.medications)
            dao.saveGoals(payload.goals)
            dao.saveConstraints(payload.constraints)
            dao.savePlans(payload.plans)
            dao.savePlanItems(payload.planItems)
            dao.saveCheckIns(payload.checkIns)
            dao.saveRiskAlerts(payload.riskAlerts)
            dao.saveConsentReceipts(payload.consentReceipts)
            dao.saveBackupManifests(payload.backupManifests)
        }
        return reconcilePlanIntegrations(
            items = payload.planItems,
            oldPlanItemIds = oldPlanItemIds,
            oldCalendarLinks = oldCalendarLinks,
            replaceStoredLinks = true,
        )
    }

    suspend fun rebuildPlanIntegrations(): PlanIntegrationResult {
        val items = dao.getPlanItems()
        return reconcilePlanIntegrations(
            items = items,
            oldPlanItemIds = items.map { it.id },
            oldCalendarLinks = dao.getCalendarLinks(),
            replaceStoredLinks = true,
        )
    }

    private suspend fun reconcilePlanIntegrations(
        items: List<PlanItemEntity>,
        oldPlanItemIds: List<String> = emptyList(),
        oldCalendarLinks: List<CalendarLinkEntity> = emptyList(),
        replaceStoredLinks: Boolean = false,
    ): PlanIntegrationResult {
        val actions = RestorePlanPolicy.actions(items, System.currentTimeMillis(), gson)
        return RestorePlanReconciler.reconcile(
            oldPlanItemIds = oldPlanItemIds,
            oldCalendarLinks = oldCalendarLinks,
            actions = actions,
            replaceStoredLinks = replaceStoredLinks,
            effects = object : RestorePlanEffects {
                override fun cancelReminders(itemId: String) {
                    ReminderWorker.cancel(context, itemId)
                }

                override fun deleteCalendarEvent(link: CalendarLinkEntity): Boolean =
                    calendar.deleteOwnedEvent(link)

                override suspend fun replaceCalendarLinks(links: List<CalendarLinkEntity>) {
                    database.withTransaction {
                        dao.clearCalendarLinks()
                        if (links.isNotEmpty()) dao.saveCalendarLinks(links)
                    }
                }

                override fun insertCalendarEvent(item: PlanItemEntity): CalendarLinkEntity? =
                    calendar.insertPlanItem(item)

                override suspend fun saveCalendarLink(link: CalendarLinkEntity) {
                    dao.saveCalendarLink(link)
                }

                override fun scheduleReminder(item: PlanItemEntity, reminderMinutes: Int) {
                    ReminderWorker.schedule(context, item, reminderMinutes)
                }
            },
        )
    }

    private suspend fun buildAiContext(start: Instant, end: Instant): AiContextSnapshotDto {
        val profile = dao.getProfile() ?: error("请先完成个人画像")
        val health = dao.getHealthRecords().take(100)
        val goals = dao.getGoals()
        val checkIns = dao.getCheckIns().take(30)
        val feedback = listOf(FeedbackAdjuster.evaluate(checkIns).toPrompt()) + checkIns.map {
            "${it.status}，难度 ${it.difficulty}/5，精力 ${it.energy}/5：${it.note.take(80)}"
        }
        return AiContextMinimizer.build(
            profile = profile,
            health = health,
            goals = goals,
            busyBlocks = calendar.readBusyBlocks(start, end),
            recentFeedback = feedback,
            medications = dao.getMedications(),
            gson = gson,
        )
    }

    private suspend fun assessUserRisk(focus: String): com.project011.lifehealthplanner.domain.PlanningRiskAssessment {
        val profile = dao.getProfile()
        val profileText = profile?.let { "${it.conditionsJson}\n${it.allergiesJson}" }.orEmpty()
        return RiskDetector.assessForPlanning(
            focus = focus,
            profileText = profileText,
            healthRecords = dao.getHealthRecords(),
        )
    }

    private fun PlanValidationResult.withUserRisk(
        assessment: com.project011.lifehealthplanner.domain.PlanningRiskAssessment,
    ): PlanValidationResult {
        if (assessment.level != RiskLevel.URGENT) return this
        return PlanValidationResult(
            isValid = false,
            errors = (errors + assessment.message).distinct(),
        )
    }

    private suspend fun recordConsent(endpoint: String, contextSnapshot: AiContextSnapshotDto) {
        val fields = buildList {
            add("年龄段")
            add("地区与时区")
            if (contextSnapshot.healthConstraints.isNotEmpty()) add("健康约束")
            if (contextSnapshot.goals.isNotEmpty()) add("目标")
            if (contextSnapshot.metrics.isNotEmpty()) add("近期健康指标")
            if (contextSnapshot.busyBlocks.isNotEmpty()) add("匿名忙碌时段")
            if (contextSnapshot.recentFeedback.isNotEmpty()) add("执行反馈")
            if (contextSnapshot.weeklyBudget != null) add("周预算")
        }
        dao.saveConsentReceipt(
            AiConsentReceiptEntity(
                id = UUID.randomUUID().toString(),
                endpoint = endpoint,
                fieldSummaryJson = gson.toJson(fields),
            ),
        )
    }

    private suspend fun exportPayload() = BackupPayload(
        exportedAt = System.currentTimeMillis(),
        profile = dao.getProfile(),
        healthRecords = dao.getHealthRecords(),
        medications = dao.getMedications(),
        goals = dao.getGoals(),
        constraints = dao.getConstraints(),
        plans = dao.getPlans(),
        planItems = dao.getPlanItems(),
        checkIns = dao.getCheckIns(),
        riskAlerts = dao.getRiskAlerts(),
        calendarLinks = dao.getCalendarLinks(),
        consentReceipts = dao.getConsentReceipts(),
        backupManifests = dao.getBackupManifests(),
    )

    private fun parseStrings(json: String): List<String> = runCatching {
        gson.fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type)
    }.getOrDefault(emptyList())

    private fun PlanItemDto.toEntity(planId: String) = PlanItemEntity(
        id = PlanPersistence.itemId(planId, id),
        planId = planId,
        domain = domain,
        title = title,
        description = description,
        startAt = Instant.parse(startAt).toEpochMilli(),
        endAt = Instant.parse(endAt).toEpochMilli(),
        priority = priority,
        energy = energy,
        estimatedCost = estimatedCost,
        goalIdsJson = gson.toJson(goalIds),
        reminderMinutesJson = gson.toJson(reminderMinutes),
        safetyTagsJson = gson.toJson(safetyTags),
    )

}

private const val ON_DEVICE_STEP_RECORD_ID = "on-device-step-counter"

data class PlanConfirmationResult(
    val planId: String,
    val itemCount: Int,
    val calendarCount: Int,
    val reminderCount: Int,
    val calendarPendingCount: Int = 0,
    val reminderFailureCount: Int = 0,
) {
    fun userMessage(): String = buildString {
        append("计划已确认，共 $itemCount 项")
        append("；写入专属日历 $calendarCount 项")
        append("；安排提醒 $reminderCount 个")
        if (calendarPendingCount > 0) append("；日历待补建 $calendarPendingCount 项")
        if (reminderFailureCount > 0) append("；提醒待重试 $reminderFailureCount 个")
    }
}
