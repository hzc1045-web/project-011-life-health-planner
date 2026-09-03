package com.project011.lifehealthplanner.domain

import com.project011.lifehealthplanner.data.local.CheckInEntity

data class FeedbackAdjustment(
    val loadFactor: Double,
    val guidance: String,
) {
    fun toPrompt(): String = "下一周期负荷系数 ${"%.2f".format(loadFactor)}：$guidance"
}

object FeedbackAdjuster {
    fun evaluate(checkIns: List<CheckInEntity>): FeedbackAdjustment {
        if (checkIns.isEmpty()) return FeedbackAdjustment(1.0, "暂无执行反馈，保持保守负荷")
        val completionScore = checkIns.map {
            when (it.status) {
                "completed" -> 1.0
                "partial" -> 0.5
                else -> 0.0
            }
        }.average()
        val averageDifficulty = checkIns.map { it.difficulty }.average()
        val averageEnergy = checkIns.map { it.energy }.average()
        return when {
            completionScore < 0.5 || averageDifficulty >= 4.0 || averageEnergy <= 2.0 ->
                FeedbackAdjustment(0.8, "减少任务数量或时长，优先恢复和关键目标")
            completionScore >= 0.85 && averageDifficulty <= 2.5 && averageEnergy >= 3.5 ->
                FeedbackAdjustment(1.1, "可小幅增加一个高优先级任务，避免同时增加多个领域")
            else -> FeedbackAdjustment(1.0, "保持当前负荷并调整未完成项目的时间位置")
        }
    }
}
