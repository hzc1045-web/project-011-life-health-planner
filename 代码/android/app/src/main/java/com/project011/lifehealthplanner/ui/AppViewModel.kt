package com.project011.lifehealthplanner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.project011.lifehealthplanner.LifeHealthApplication
import com.project011.lifehealthplanner.data.local.LifeGoalEntity
import com.project011.lifehealthplanner.data.local.MedicationEntity
import com.project011.lifehealthplanner.data.local.UserProfileEntity
import com.project011.lifehealthplanner.domain.PlanPeriod
import com.project011.lifehealthplanner.domain.RiskDetector
import com.project011.lifehealthplanner.domain.RiskLevel
import com.project011.lifehealthplanner.health.HealthConnectAvailability
import com.project011.lifehealthplanner.health.HealthConnectUiStatus
import com.project011.lifehealthplanner.health.StepCounterTimeoutException
import com.project011.lifehealthplanner.health.StepCounterUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import java.io.IOException
import java.net.SocketTimeoutException
import retrofit2.HttpException

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as LifeHealthApplication).repository
    private val transient = MutableStateFlow(AppUiState())

    private val personalData = combine(
        repository.profile,
        repository.goals,
        repository.healthRecords,
        repository.medications,
    ) { profile, goals, healthRecords, medications ->
        PersonalData(profile, goals, healthRecords, medications)
    }

    private val planningData = combine(repository.plans, repository.planItems) { plans, planItems ->
        PlanningData(plans, planItems)
    }

    val state: StateFlow<AppUiState> = combine(
        personalData,
        planningData,
        transient,
    ) { personal, planning, current ->
        current.copy(
            profile = personal.profile,
            goals = personal.goals,
            healthRecords = personal.healthRecords,
            medications = personal.medications,
            plans = planning.plans,
            planItems = planning.planItems,
            paired = repository.isPaired(),
            companionServer = repository.companionServer(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    init {
        refreshHealthConnectStatus()
        if (repository.isPaired()) {
            viewModelScope.launch { runCatching { updateCompanionStatus() } }
        }
    }

    fun clearMessage() = transient.update { it.copy(message = null) }

    fun saveOnboarding(
        displayName: String,
        birthDate: String,
        ageBand: String,
        heightCm: Double?,
        weightKg: Double?,
        region: String,
        emergencyNumber: String,
        conditions: String,
        allergies: String,
        preferences: String,
        workSchedule: String,
        sleepHours: Double,
        weeklyBudget: Double?,
    ) = launchAction("个人画像已保存") {
        val gson = Gson()
        repository.saveProfile(
            UserProfileEntity(
                displayName = displayName.trim(),
                birthDate = birthDate.trim(),
                ageBand = ageBand,
                heightCm = heightCm,
                weightKg = weightKg,
                region = region.trim(),
                emergencyNumber = emergencyNumber.trim(),
                conditionsJson = gson.toJson(splitList(conditions)),
                allergiesJson = gson.toJson(splitList(allergies)),
                preferencesJson = gson.toJson(splitList(preferences)),
                workSchedule = workSchedule.trim(),
                sleepHours = sleepHours,
                weeklyBudget = weeklyBudget,
                timezone = java.time.ZoneId.systemDefault().id,
                appLockEnabled = false,
                onboardingComplete = true,
            ),
        )
    }

    fun setAppLock(enabled: Boolean) {
        val profile = state.value.profile ?: return
        launchAction(if (enabled) "应用锁已开启" else "应用锁已关闭") {
            repository.saveProfile(profile.copy(appLockEnabled = enabled, updatedAt = System.currentTimeMillis()))
        }
    }

    fun addGoal(domain: String, title: String, target: String, priority: Int) =
        launchAction("目标已添加") {
            require(title.isNotBlank()) { "请输入目标名称" }
            repository.saveGoal(
                LifeGoalEntity(
                    id = UUID.randomUUID().toString(),
                    domain = domain,
                    title = title.trim(),
                    target = target.trim(),
                    priority = priority.coerceIn(1, 5),
                ),
            )
        }

    fun addMedication(name: String, ingredient: String, dose: String, schedule: String) =
        launchAction("药物记录已保存") {
            require(name.isNotBlank()) { "请输入药物名称" }
            repository.saveMedication(
                MedicationEntity(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    activeIngredient = ingredient.trim(),
                    dose = dose.trim(),
                    schedule = schedule.trim(),
                ),
            )
        }

    fun addHealthRecord(kind: String, value: String, unit: String) = launchAction("健康记录已保存") {
        val parsed = value.toDoubleOrNull() ?: error("请输入有效数值")
        repository.saveManualHealth(kind, parsed, unit)
    }

    fun syncHealthConnect() = launchAction(null) {
        val result = repository.syncHealthConnect()
        transient.update {
            it.copy(
                healthGrantedPermissionCount = result.grantedTypeCount,
                message = result.userMessage(),
            )
        }
    }

    /** Reads the phone hardware counter; this remains usable without Health Connect. */
    fun readOnDeviceSteps() = launchAction(null) {
        val snapshot = repository.readOnDeviceSteps()
        transient.update {
            it.copy(
                onDeviceStepPermissionGranted = true,
                message = "已读取本机步数 ${snapshot.stepsSinceBoot} 步（自最近一次开机累计；不含 vivo/iQOO 历史数据）",
            )
        }
    }

    fun onDeviceStepPermissionResult(granted: Boolean) {
        transient.update { it.copy(onDeviceStepPermissionGranted = granted) }
        if (granted) {
            readOnDeviceSteps()
        } else {
            transient.update { it.copy(message = "未授予“身体活动”权限，无法读取本机步数") }
        }
    }

    fun generatePlan(focus: String, useAi: Boolean, days: Int) = launchAction(null) {
        val snapshot = state.value
        val profileText = snapshot.profile?.let {
            // The profile is stored as JSON arrays; matching the serialized
            // text still catches explicit emergency phrases without exposing
            // any additional data to the remote service.
            "${it.conditionsJson}\n${it.allergiesJson}"
        }.orEmpty()
        val risk = RiskDetector.assessForPlanning(
            focus = focus,
            profileText = profileText,
            healthRecords = snapshot.healthRecords,
        )
        require(risk.level != RiskLevel.URGENT) {
            val emergencyNumber = state.value.profile?.emergencyNumber.orEmpty().ifBlank { "当地急救服务" }
            "${risk.message}，已停止生成普通计划，请立即联系 $emergencyNumber"
        }
        val period = PlanPeriod.forDays(Instant.now(), days)
        val start = period.start
        val end = period.end
        val draftAndValidation = if (useAi) {
            repository.requestAiPlan(focus, start, end, state.value.companionProviderId)
        } else {
            val draft = repository.createOfflinePlan(start, end)
            draft to repository.validateDraft(draft, start, end)
        }
        transient.update {
            it.copy(
                draft = draftAndValidation.first,
                draftValidation = draftAndValidation.second,
                draftPeriodStart = start.toEpochMilli(),
                draftPeriodEnd = end.toEpochMilli(),
                message = if (draftAndValidation.second.isValid) "计划草案已生成" else "草案需要调整",
            )
        }
    }

    fun confirmDraft() = launchAction(null) {
        val snapshot = state.value
        val draft = snapshot.draft ?: error("没有待确认计划")
        val startMillis = snapshot.draftPeriodStart ?: error("草案周期已失效，请重新生成")
        val endMillis = snapshot.draftPeriodEnd ?: error("草案周期已失效，请重新生成")
        val period = PlanPeriod.fromEpochMillis(startMillis, endMillis)
        val confirmation = repository.confirmPlan(draft, period.start, period.end)
        transient.update {
            it.copy(
                draft = null,
                draftValidation = null,
                draftPeriodStart = null,
                draftPeriodEnd = null,
                message = confirmation.userMessage(),
            )
        }
    }

    fun checkIn(itemId: String, status: String, difficulty: Int, energy: Int, note: String) =
        launchAction("执行状态已更新") {
            repository.checkIn(itemId, status, difficulty, energy, note)
        }

    fun prepareExport() = launchAction(null) {
        transient.update { it.copy(exportJson = repository.exportDataJson()) }
    }

    fun exportHandled(success: Boolean) {
        transient.update {
            it.copy(
                exportJson = null,
                message = if (success) "数据已导出" else "数据导出已取消",
            )
        }
    }

    fun pair(serverUrl: String, code: String) = launchAction("电脑配对成功") {
        repository.pair(serverUrl.trim(), code.trim(), android.os.Build.MODEL)
        updateCompanionStatus()
    }

    fun refreshCompanionStatus() = launchAction(null) {
        updateCompanionStatus()
        transient.update { it.copy(message = "AI 提供商状态已刷新") }
    }

    fun clearPairing() {
        repository.clearPairing()
        transient.update {
            it.copy(
                companionProviderId = "",
                companionProvider = "",
                companionProviderThirdParty = false,
                companionConnectionError = null,
                aiConfigured = false,
                message = "手机端配对信息已清除",
            )
        }
    }

    fun sendChat(message: String) = launchAction(null) {
        require(message.isNotBlank()) { "请输入内容" }
        transient.update { it.copy(chat = it.chat + ChatTurn(true, message.trim())) }
        val summary = state.value.chat.takeLast(6).joinToString("\n") { it.text.take(300) }
        val reply = repository.requestChat(
            message.trim(),
            summary,
            state.value.companionProviderId,
        )
        transient.update {
            it.copy(chat = it.chat + ChatTurn(false, reply.reply, reply), message = null)
        }
    }

    fun backup(password: String) = launchAction(null) {
        val id = repository.uploadEncryptedBackup(password.toCharArray())
        transient.update { it.copy(message = "加密备份已保存：$id") }
    }

    fun restore(password: String) = launchAction(null) {
        val integrations = repository.restoreLatestBackup(password.toCharArray())
        transient.update {
            it.copy(message = "最新备份已恢复；${integrations.userMessage()}")
        }
    }

    fun onCalendarPermissionResult(granted: Boolean) {
        if (!granted) {
            transient.update { it.copy(message = "未取得完整日历权限；计划仍保存在应用内") }
            return
        }
        launchAction(null) {
            val integrations = repository.rebuildPlanIntegrations()
            transient.update {
                it.copy(message = "日历权限已更新；${integrations.userMessage()}")
            }
        }
    }

    fun healthPermissionContract() = repository.healthPermissionContract()

    fun healthPermissions() = repository.healthPermissions()

    fun refreshHealthConnectStatus() {
        val devicePermissionGranted = repository.healthOnDeviceStepPermissionGranted()
        if (repository.healthSdkStatus() != androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE) {
            transient.update {
                it.copy(
                    healthGrantedPermissionCount = 0,
                    onDeviceStepPermissionGranted = devicePermissionGranted,
                )
            }
            return
        }
        viewModelScope.launch {
            try {
                val count = repository.healthGrantedPermissionCount()
                transient.update {
                    it.copy(
                        healthGrantedPermissionCount = count,
                        onDeviceStepPermissionGranted = devicePermissionGranted,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                transient.update {
                    it.copy(
                        healthGrantedPermissionCount = null,
                        onDeviceStepPermissionGranted = devicePermissionGranted,
                    )
                }
            }
        }
    }

    fun healthConnectUiStatus(): HealthConnectUiStatus {
        val availability = when (repository.healthSdkStatus()) {
            androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE ->
                HealthConnectAvailability.AVAILABLE
            androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.UPDATE_REQUIRED
            else -> HealthConnectAvailability.UNAVAILABLE
        }
        return HealthConnectUiStatus(
            availability = availability,
            hasSyncedRecords = state.value.healthRecords.any { it.source.startsWith("health_connect:") },
            onDeviceStepCountingAvailable = repository.healthOnDeviceStepCountingAvailable(),
            onDeviceStepPermissionGranted = state.value.onDeviceStepPermissionGranted,
            grantedPermissionCount = state.value.healthGrantedPermissionCount,
            requestedPermissionCount = repository.healthPermissions().size,
        )
    }

    fun healthConnectAvailable() = healthConnectUiStatus().isAvailable

    fun healthConnectStatusMessage() = healthConnectUiStatus().statusMessage()

    fun healthConnectNeedsUpdate() = healthConnectUiStatus().needsUpdate

    fun openHealthConnectManagement() = launchAction(null) {
        repository.openHealthConnectManagement()
    }

    private suspend fun updateCompanionStatus() {
        try {
            val status = repository.companionStatus()
            transient.update {
                it.copy(
                    companionProviderId = status.activeProvider,
                    companionProvider = status.providerDisplayName,
                    companionProviderThirdParty = status.providerIsThirdParty,
                    companionConnectionError = null,
                    aiConfigured = status.aiConfigured,
                )
            }
        } catch (error: Exception) {
            transient.update {
                it.copy(
                    companionConnectionError = "电脑中转暂时离线",
                    aiConfigured = false,
                )
            }
            throw error
        }
    }

    private fun launchAction(success: String?, action: suspend () -> Unit) {
        viewModelScope.launch {
            transient.update { it.copy(loading = true, message = null) }
            try {
                action()
                if (success != null) transient.update { state -> state.copy(message = success) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                transient.update { state ->
                    state.copy(message = userFacingMessage(error))
                }
            } finally {
                transient.update { it.copy(loading = false) }
            }
        }
    }

    private fun userFacingMessage(error: Throwable): String {
        if (error is HttpException) {
            val detail = runCatching {
                @Suppress("UNCHECKED_CAST")
                (Gson().fromJson(error.response()?.errorBody()?.string(), Map::class.java)["detail"] as? String)
            }.getOrNull()
            return detail ?: "电脑中转请求失败（${error.code()}）"
        }
        return when (error) {
            is StepCounterTimeoutException ->
                "暂未读取到本机计步器数据，请保持应用打开并走动几步后重试"
            is StepCounterUnavailableException ->
                "本机没有可用的硬件计步器，请使用 Health Connect 或手工记录"
            is SocketTimeoutException -> "连接超时，请确认电脑中转和 Tailscale 在线"
            is IOException -> "无法连接电脑中转，请确认电脑和 Tailscale 在线"
            else -> error.message ?: "操作失败，请稍后重试"
        }
    }

    private fun splitList(value: String): List<String> = value
        .split(',', '，', '\n')
        .map(String::trim)
        .filter(String::isNotBlank)

    private data class PersonalData(
        val profile: UserProfileEntity?,
        val goals: List<LifeGoalEntity>,
        val healthRecords: List<com.project011.lifehealthplanner.data.local.HealthRecordEntity>,
        val medications: List<MedicationEntity>,
    )

    private data class PlanningData(
        val plans: List<com.project011.lifehealthplanner.data.local.PlanEntity>,
        val planItems: List<com.project011.lifehealthplanner.data.local.PlanItemEntity>,
    )
}
