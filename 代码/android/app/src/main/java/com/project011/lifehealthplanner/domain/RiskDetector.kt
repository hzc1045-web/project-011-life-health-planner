package com.project011.lifehealthplanner.domain

enum class RiskLevel { NORMAL, CAUTION, URGENT }

object RiskDetector {
    private val urgent = listOf(
        Regex("胸痛.*(呼吸|出汗|晕)"),
        Regex("呼吸(非常|严重)?困难"),
        Regex("无法呼吸"),
        Regex("一侧.*(无力|麻木)"),
        Regex("言语不清"),
        Regex("大量出血"),
        Regex("失去意识"),
        Regex("自杀|轻生|伤害自己"),
    )
    private val caution = listOf(
        Regex("持续发烧"),
        Regex("反复头晕"),
        Regex("药物.*(过敏|皮疹)"),
        Regex("血压.*(很高|很低)"),
    )

    fun detect(text: String): RiskLevel = when {
        urgent.any { it.containsMatchIn(text) } -> RiskLevel.URGENT
        caution.any { it.containsMatchIn(text) } -> RiskLevel.CAUTION
        else -> RiskLevel.NORMAL
    }
}
