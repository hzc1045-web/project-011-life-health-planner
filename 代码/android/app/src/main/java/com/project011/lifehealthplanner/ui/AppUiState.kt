package com.project011.lifehealthplanner.ui

import com.project011.lifehealthplanner.data.local.HealthRecordEntity
import com.project011.lifehealthplanner.data.local.LifeGoalEntity
import com.project011.lifehealthplanner.data.local.MedicationEntity
import com.project011.lifehealthplanner.data.local.PlanEntity
import com.project011.lifehealthplanner.data.local.PlanItemEntity
import com.project011.lifehealthplanner.data.local.UserProfileEntity
import com.project011.lifehealthplanner.data.remote.ChatReplyDto
import com.project011.lifehealthplanner.data.remote.PlanDraftDto
import com.project011.lifehealthplanner.domain.PlanValidationResult

data class AppUiState(
    val profile: UserProfileEntity? = null,
    val goals: List<LifeGoalEntity> = emptyList(),
    val healthRecords: List<HealthRecordEntity> = emptyList(),
    val medications: List<MedicationEntity> = emptyList(),
    val plans: List<PlanEntity> = emptyList(),
    val planItems: List<PlanItemEntity> = emptyList(),
    val draft: PlanDraftDto? = null,
    val draftValidation: PlanValidationResult? = null,
    val chat: List<ChatTurn> = emptyList(),
    val exportJson: String? = null,
    val paired: Boolean = false,
    val companionServer: String = "",
    val loading: Boolean = false,
    val message: String? = null,
)

data class ChatTurn(
    val fromUser: Boolean,
    val text: String,
    val reply: ChatReplyDto? = null,
)
