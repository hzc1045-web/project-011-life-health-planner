package com.project011.lifehealthplanner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.project011.lifehealthplanner.LifeHealthApplication
import com.project011.lifehealthplanner.data.local.LifeGoalEntity
import com.project011.lifehealthplanner.data.local.MedicationEntity
import com.project011.lifehealthplanner.data.local.UserProfileEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

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
                appLockEnabled = true,
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
        val count = repository.syncHealthConnect()
        transient.update { it.copy(message = "已同步 $count 条健康记录") }
    }

    fun generatePlan(focus: String, useAi: Boolean, days: Int) = launchAction(null) {
        val start = Instant.now()
        val end = start.plus(days.coerceIn(1, 31).toLong(), ChronoUnit.DAYS)
        val draftAndValidation = if (useAi) {
            repository.requestAiPlan(focus, start, end)
        } else {
            val draft = repository.createOfflinePlan(start, end)
            draft to repository.validateDraft(draft, start, end)
        }
        transient.update {
            it.copy(
                draft = draftAndValidation.first,
                draftValidation = draftAndValidation.second,
                message = if (draftAndValidation.second.isValid) "计划草案已生成" else "草案需要调整",
            )
        }
    }

    fun confirmDraft() = launchAction("计划已确认；已授权时同步到专属日历") {
        val draft = state.value.draft ?: error("没有待确认计划")
        val start = Instant.now().minus(1, ChronoUnit.MINUTES)
        val end = start.plus(7, ChronoUnit.DAYS).plus(5, ChronoUnit.MINUTES)
        repository.confirmPlan(draft, start, end)
        transient.update { it.copy(draft = null, draftValidation = null) }
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
    }

    fun clearPairing() {
        repository.clearPairing()
        transient.update { it.copy(message = "手机端配对信息已清除") }
    }

    fun sendChat(message: String) = launchAction(null) {
        require(message.isNotBlank()) { "请输入内容" }
        transient.update { it.copy(chat = it.chat + ChatTurn(true, message.trim())) }
        val summary = state.value.chat.takeLast(6).joinToString("\n") { it.text.take(300) }
        val reply = repository.requestChat(message.trim(), summary)
        transient.update {
            it.copy(chat = it.chat + ChatTurn(false, reply.reply, reply), message = null)
        }
    }

    fun backup(password: String) = launchAction(null) {
        val id = repository.uploadEncryptedBackup(password.toCharArray())
        transient.update { it.copy(message = "加密备份已保存：$id") }
    }

    fun restore(password: String) = launchAction("最新备份已恢复") {
        repository.restoreLatestBackup(password.toCharArray())
    }

    fun healthPermissionContract() = repository.healthPermissionContract()

    fun healthPermissions() = repository.healthPermissions()

    fun healthConnectAvailable() =
        repository.healthSdkStatus() == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE

    private fun launchAction(success: String?, action: suspend () -> Unit) {
        viewModelScope.launch {
            transient.update { it.copy(loading = true, message = null) }
            runCatching { action() }
                .onSuccess { if (success != null) transient.update { state -> state.copy(message = success) } }
                .onFailure { error ->
                    transient.update { state ->
                        state.copy(message = error.message ?: "操作失败，请稍后重试")
                    }
                }
            transient.update { it.copy(loading = false) }
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
