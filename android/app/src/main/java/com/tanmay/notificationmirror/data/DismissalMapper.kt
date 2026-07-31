package com.tanmay.notificationmirror.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts the notification key from a realtime UPDATE record if the row was
 * dismissed. Returns null for anything else, including a payload missing the
 * key because the table lacks REPLICA IDENTITY FULL.
 */
fun dismissedKeyOrNull(record: JsonObject): String? {
    val dismissed = record["is_dismissed"]?.jsonPrimitive?.booleanOrNull ?: false
    if (!dismissed) return null
    val key = record["notification_key"] ?: return null
    if (key is JsonNull) return null
    return key.jsonPrimitive.content
}
