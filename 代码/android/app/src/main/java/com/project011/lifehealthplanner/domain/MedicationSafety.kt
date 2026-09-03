package com.project011.lifehealthplanner.domain

import com.project011.lifehealthplanner.data.local.MedicationEntity

object MedicationSafety {
    fun warnings(medications: List<MedicationEntity>, allergies: List<String>): List<String> {
        val warnings = mutableListOf<String>()
        medications
            .filter { it.activeIngredient.isNotBlank() }
            .groupBy { normalize(it.activeIngredient) }
            .filterValues { it.size > 1 }
            .forEach { (_, matches) ->
                warnings += "发现重复有效成分：${matches.joinToString { it.activeIngredient }}"
            }
        medications.forEach { medication ->
            val text = normalize("${medication.name} ${medication.activeIngredient}")
            allergies.filter { it.isNotBlank() }.forEach { allergy ->
                if (text.contains(normalize(allergy))) {
                    warnings += "${medication.name} 可能与已记录过敏项“$allergy”相关，请咨询医生或药师"
                }
            }
        }
        if (medications.isNotEmpty()) {
            warnings += "本应用未接入专业药物相互作用数据库，新药或联合用药请让医生或药师复核"
        }
        return warnings.distinct()
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[\\s,，.。()（）-]"), "")
}
