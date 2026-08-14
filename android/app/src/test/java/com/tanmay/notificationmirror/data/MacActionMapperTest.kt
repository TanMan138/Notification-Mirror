package com.tanmay.notificationmirror.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MacActionMapperTest {

    @Test
    fun `reports a dismissal when the row is dismissed`() {
        val record = buildJsonObject {
            put("notification_key", "0|com.example|1|null|10123")
            put("is_dismissed", true)
        }

        assertEquals(MacAction.Dismissal("0|com.example|1|null|10123"), macActionOrNull(record))
    }

    @Test
    fun `ignores a row that is not dismissed and carries no reply`() {
        val record = buildJsonObject {
            put("notification_key", "key")
            put("is_dismissed", false)
        }

        assertNull(macActionOrNull(record))
    }

    @Test
    fun `ignores a row with no is_dismissed column`() {
        val record = buildJsonObject {
            put("notification_key", "key")
        }

        assertNull(macActionOrNull(record))
    }

    @Test
    fun `ignores a dismissed row with no key rather than throwing`() {
        val record = buildJsonObject {
            put("is_dismissed", true)
        }

        assertNull(macActionOrNull(record))
    }

    @Test
    fun `ignores a dismissed row whose key is null`() {
        val record = buildJsonObject {
            put("notification_key", JsonNull)
            put("is_dismissed", true)
        }

        assertNull(macActionOrNull(record))
    }

    @Test
    fun `reports a reply when the Mac marks the row pending`() {
        val record = buildJsonObject {
            put("id", "e719a6c7-e5c9-4e90-ba2c-92ef86bfbe2e")
            put("notification_key", "key")
            put("is_dismissed", false)
            put("reply_text", "on my way")
            put("reply_status", "pending")
        }

        assertEquals(
            MacAction.Reply("e719a6c7-e5c9-4e90-ba2c-92ef86bfbe2e", "key", "on my way"),
            macActionOrNull(record)
        )
    }

    @Test
    fun `carries a numeric row id as text`() {
        // The documented schema uses bigint; older projects use uuid. The
        // outcome is written back with a filter on this value either way.
        val record = buildJsonObject {
            put("id", 42)
            put("notification_key", "key")
            put("reply_text", "on my way")
            put("reply_status", "pending")
        }

        assertEquals(MacAction.Reply("42", "key", "on my way"), macActionOrNull(record))
    }

    @Test
    fun `still replies when the row carries no id`() {
        val record = buildJsonObject {
            put("notification_key", "key")
            put("reply_text", "on my way")
            put("reply_status", "pending")
        }

        assertEquals(MacAction.Reply(null, "key", "on my way"), macActionOrNull(record))
    }

    @Test
    fun `ignores the phone's own outcome write`() {
        val record = buildJsonObject {
            put("notification_key", "key")
            put("is_dismissed", false)
            put("reply_text", "on my way")
            put("reply_status", "sent")
        }

        assertNull(macActionOrNull(record))
    }

    @Test
    fun `ignores a failed outcome so it is not retried in a loop`() {
        val record = buildJsonObject {
            put("notification_key", "key")
            put("reply_text", "on my way")
            put("reply_status", "failed")
        }

        assertNull(macActionOrNull(record))
    }

    @Test
    fun `ignores a pending row with no text`() {
        val record = buildJsonObject {
            put("notification_key", "key")
            put("reply_status", "pending")
            put("reply_text", JsonNull)
        }

        assertNull(macActionOrNull(record))
    }

    @Test
    fun `treats a dismissal as winning over a pending reply on the same row`() {
        val record = buildJsonObject {
            put("notification_key", "key")
            put("is_dismissed", true)
            put("reply_text", "too late")
            put("reply_status", "pending")
        }

        assertEquals(MacAction.Dismissal("key"), macActionOrNull(record))
    }
}
