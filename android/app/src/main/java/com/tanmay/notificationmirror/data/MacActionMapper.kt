package com.tanmay.notificationmirror.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Something the Mac asked the phone to do, carried as an UPDATE to the row. */
sealed interface MacAction {
    data class Dismissal(val key: String) : MacAction
    /**
     * [rowId] is the row's primary key, as text — uuid on older projects,
     * bigint on ones built from the documented SQL. The outcome is written
     * back against it, because one notification_key covers every message in a
     * conversation: filtering the outcome on the key rewrites the status of
     * every past reply and the Mac announces each one as freshly failed.
     */
    data class Reply(val rowId: String?, val key: String, val text: String) : MacAction
}

/**
 * Classifies a realtime UPDATE record. Returns null for anything the phone
 * should not act on, including its own reply outcome writes and a payload
 * missing the key because the table lacks REPLICA IDENTITY FULL.
 */
fun macActionOrNull(record: JsonObject): MacAction? {
    val key = record["notification_key"].contentOrNull() ?: return null

    if (record["is_dismissed"]?.jsonPrimitive?.booleanOrNull == true) {
        return MacAction.Dismissal(key)
    }

    // Only 'pending' is the Mac talking. 'sent' and 'failed' are this device's
    // own writes coming back around, and acting on them would loop.
    if (record["reply_status"].contentOrNull() != "pending") return null
    val text = record["reply_text"].contentOrNull() ?: return null
    return MacAction.Reply(record["id"].contentOrNull(), key, text)
}

private fun JsonElement?.contentOrNull(): String? =
    if (this == null || this is JsonNull) null else jsonPrimitive.content
