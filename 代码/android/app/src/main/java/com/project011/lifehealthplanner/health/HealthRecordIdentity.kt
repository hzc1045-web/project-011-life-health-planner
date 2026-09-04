package com.project011.lifehealthplanner.health

import java.time.Instant
import java.util.UUID

internal object HealthRecordIdentity {
    fun create(kind: String, externalId: String, source: String, time: Instant): String {
        val recordKey = externalId.trim().takeIf(String::isNotEmpty)
            ?.let { "id:$it" }
            ?: "time:${time.toEpochMilli()}"
        val identity = "$kind|$source|$recordKey"
        return "hc-${UUID.nameUUIDFromBytes(identity.toByteArray(Charsets.UTF_8))}"
    }
}
