package com.project011.lifehealthplanner.data

internal object PlanPersistence {
    fun itemId(planId: String, draftItemId: String): String = "$planId:$draftItemId"
}
