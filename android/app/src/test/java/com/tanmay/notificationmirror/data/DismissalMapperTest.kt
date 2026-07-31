package com.tanmay.notificationmirror.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DismissalMapperTest {

    @Test
    fun `returns the key when the row is dismissed`() {
        val record = buildJsonObject {
            put("notification_key", "0|com.example|1|null|10123")
            put("is_dismissed", true)
        }

        assertEquals("0|com.example|1|null|10123", dismissedKeyOrNull(record))
    }

    @Test
    fun `ignores a row that is not dismissed`() {
        val record = buildJsonObject {
            put("notification_key", "key")
            put("is_dismissed", false)
        }

        assertNull(dismissedKeyOrNull(record))
    }

    @Test
    fun `ignores a row with no is_dismissed column`() {
        val record = buildJsonObject {
            put("notification_key", "key")
        }

        assertNull(dismissedKeyOrNull(record))
    }

    @Test
    fun `ignores a dismissed row with no key rather than throwing`() {
        val record = buildJsonObject {
            put("is_dismissed", true)
        }

        assertNull(dismissedKeyOrNull(record))
    }

    @Test
    fun `ignores a dismissed row whose key is null`() {
        val record = buildJsonObject {
            put("notification_key", JsonNull)
            put("is_dismissed", true)
        }

        assertNull(dismissedKeyOrNull(record))
    }
}
