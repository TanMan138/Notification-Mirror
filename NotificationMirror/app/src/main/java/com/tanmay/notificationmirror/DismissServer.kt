package com.tanmay.notificationmirror

import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.InputStream

/**
 * HTTP server that listens for dismiss requests.
 * When it receives POST /dismiss with JSON {"key": "..."}, invokes the callback
 * so NotificationMirrorService can cancel the notification.
 */
class DismissServer(
    private val onDismiss: (key: String) -> Unit
) : NanoHTTPD(8081) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method != Method.POST || session.uri != "/dismiss") {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Not found"
            )
        }

        return try {
            val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
            val body = if (contentLength > 0 && contentLength <= 64 * 1024) {
                readFully(session.inputStream, contentLength)
            } else {
                session.inputStream.readBytes().take(64 * 1024).toByteArray()
            }
            val json = JSONObject(String(body, Charsets.UTF_8))
            val key = json.optString("key")
            if (key.isNotBlank()) {
                onDismiss(key)
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    """{"status":"ok"}"""
                )
            } else {
                newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    """{"error":"missing key"}"""
                )
            }
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error":"${e.message}"}"""
            )
        }
    }

    private fun readFully(input: InputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var totalRead = 0
        while (totalRead < length) {
            val read = input.read(buffer, totalRead, length - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return buffer
    }
}
