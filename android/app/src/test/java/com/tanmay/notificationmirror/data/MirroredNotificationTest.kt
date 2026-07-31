package com.tanmay.notificationmirror.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MirroredNotificationTest {

    @Test
    fun `serializes property names to the database columns`() {
        val encoded = Json.encodeToString(
            MirroredNotification(
                notificationKey = "0|com.example|1|null|10123",
                appName = "Example",
                title = "Hello",
                bodyText = "World",
                base64Icon = "AAAA"
            )
        )

        assertEquals(
            """{"notification_key":"0|com.example|1|null|10123","app_name":"Example",""" +
                """"title":"Hello","body_text":"World","base64_icon":"AAAA"}""",
            encoded
        )
    }

    @Test
    fun `serializes a notification with no app name, body, or icon`() {
        val encoded = Json.encodeToString(
            MirroredNotification(
                notificationKey = "key",
                appName = null,
                title = "Hello",
                bodyText = null,
                base64Icon = null
            )
        )

        assertEquals(
            """{"notification_key":"key","app_name":null,"title":"Hello",""" +
                """"body_text":null,"base64_icon":null}""",
            encoded
        )
    }
}
