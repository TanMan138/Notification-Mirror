package com.tanmay.notificationmirror

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object NetworkManager {

    private const val TAG = "NotificationMirror"
    private const val NOTIFICATIONS_PATH = "/rest/v1/notifications"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    /**
     * Sends a notification payload to Supabase.
     * Runs on [Dispatchers.IO] and must be called from a coroutine.
     *
     * @param notificationKey StatusBarNotification key
     * @param appName Human-readable source app name, or null
     * @param title Notification title
     * @param bodyText Notification body text
     * @param base64Icon Source app icon as lossy WebP Base64 string, or null
     */
    suspend fun sendNotification(
        notificationKey: String,
        appName: String?,
        title: String,
        bodyText: String,
        base64Icon: String?
    ) = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            Log.e(TAG, "Supabase URL is blank. Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY to local.properties and sync Gradle.")
            return@withContext
        }

        val url = URL("$baseUrl$NOTIFICATIONS_PATH")
        val payload = JSONObject().apply {
            put("notification_key", notificationKey)
            put("app_name", appName)
            put("title", title)
            put("body_text", bodyText)
            put("base64_icon", base64Icon)
        }.toString()

        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_PUBLISHABLE_KEY}")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=minimal")
            conn.doOutput = true
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload)
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                Log.d(TAG, "POST /notifications OK $responseCode")
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.readText()?.take(200).orEmpty()
                Log.e(TAG, "POST /notifications failed: $responseCode ${conn.responseMessage} $errorBody")
                throw Exception("HTTP $responseCode: ${conn.responseMessage}")
            }
        } finally {
            conn.disconnect()
        }
    }
}
