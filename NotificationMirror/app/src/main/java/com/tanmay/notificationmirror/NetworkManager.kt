package com.tanmay.notificationmirror

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object NetworkManager {

    private const val NOTIFY_PATH = "/notify"
    private const val PORT = 8080
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    /**
     * Sends a notification payload to the given IP.
     * Runs on [Dispatchers.IO] and must be called from a coroutine.
     *
     * @param ip Target host (e.g. your Mac's local IP like "192.168.1.100")
     * @param title Notification title
     * @param text Notification body text
     * @param appName Human-readable source app name, or null
     * @param appIconBase64 Source app icon as PNG Base64 string, or null
     * @param notificationKey StatusBarNotification key for two-way dismiss, or null
     * @param phoneIp Phone's local Wi-Fi IP for dismiss callbacks, or null
     * @throws Exception on connection or I/O errors
     */
    suspend fun sendNotification(
        ip: String,
        title: String,
        text: String,
        appName: String? = null,
        appIconBase64: String? = null,
        notificationKey: String? = null,
        phoneIp: String? = null
    ) = withContext(Dispatchers.IO) {
        val url = URL("http://$ip:$PORT$NOTIFY_PATH")
        val payload = JSONObject().apply {
            put("title", title)
            put("text", text)
            put("secret", BuildConfig.NOTIFICATION_MIRROR_SECRET)
            put("appName", appName)
            put("appIconBase64", appIconBase64)
            put("notificationKey", notificationKey)
            put("phoneIp", phoneIp)
        }.toString()

        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload)
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                throw Exception("HTTP $responseCode: ${conn.responseMessage}")
            }
        } finally {
            conn.disconnect()
        }
    }
}
