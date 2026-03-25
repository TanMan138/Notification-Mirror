package com.tanmay.notificationmirror

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Base64
import android.util.Log
import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream

class NotificationMirrorService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val supabase: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    ) {
        install(Postgrest)
        install(Realtime)
    }

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            startCloudDismissListener()
        }
    }

    private suspend fun startCloudDismissListener() {
        try {
            val channel = supabase.channel("public:notifications")
            val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "notifications"
            }
            channel.subscribe(blockUntilSubscribed = true)
            changeFlow.collect { action ->
                Log.d("NotificationMirror", "Realtime event: $action")
                if (action is PostgresAction.Update) {
                    val record = action.record
                    val isDismissed = record["is_dismissed"]?.jsonPrimitive?.booleanOrNull ?: false
                    Log.d("NotificationMirror", "is_dismissed=$isDismissed parsed from $record")
                    
                    if (isDismissed) {
                        val key = record["notification_key"]?.jsonPrimitive?.content
                        if (key != null) {
                            cancelNotification(key)
                            Log.d("NotificationMirror", "Dismissed notification $key from cloud.")
                        } else {
                            Log.w("NotificationMirror", "Missing notification_key in record: $record")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NotificationMirror", "Failed to start cloud dismiss listener", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (_: Exception) {
            null
        }

        val appIconBase64 = try {
            val icon = packageManager.getApplicationIcon(sbn.packageName)
            drawableToBase64Webp(icon)
        } catch (_: Exception) {
            null
        }

        val notificationKey = sbn.key

        serviceScope.launch {
            try {
                NetworkManager.sendNotification(
                    notificationKey = notificationKey,
                    appName = appName,
                    title = title,
                    bodyText = text,
                    base64Icon = appIconBase64
                )
            } catch (e: Exception) {
                Log.e("NotificationMirror", "Failed to send notification to Supabase", e)
            }
        }
    }

    companion object {
        private const val FALLBACK_ICON_SIZE = 96
        private const val ICON_MAX_EDGE_PX = 64
        private const val WEBP_QUALITY = 70

        /**
         * Converts a [Drawable] to a lossy WebP Base64 string (max [ICON_MAX_EDGE_PX] on the long edge).
         */
        fun drawableToBase64Webp(drawable: Drawable?): String? {
            if (drawable == null) return null
            val source = try {
                drawableToBitmap(drawable)
            } catch (_: Exception) {
                null
            } ?: return null

            val toCompress = try {
                scaleBitmapToMaxEdge(source, ICON_MAX_EDGE_PX)
            } catch (_: Exception) {
                null
            } ?: return null

            val output = ByteArrayOutputStream()
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            if (!toCompress.compress(format, WEBP_QUALITY, output)) return null
            return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }

        private fun scaleBitmapToMaxEdge(bitmap: Bitmap, maxEdge: Int): Bitmap? {
            if (bitmap.isRecycled) return null
            val w = bitmap.width
            val h = bitmap.height
            if (w <= 0 || h <= 0) return null
            if (w <= maxEdge && h <= maxEdge) return bitmap
            val scale = minOf(maxEdge.toFloat() / w, maxEdge.toFloat() / h)
            val newW = (w * scale).toInt().coerceAtLeast(1)
            val newH = (h * scale).toInt().coerceAtLeast(1)
            return try {
                Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            } catch (_: Exception) {
                null
            }
        }

        private fun drawableToBitmap(drawable: Drawable): Bitmap? {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                return drawable.bitmap
            }
            val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceAtMost(512) else FALLBACK_ICON_SIZE
            val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceAtMost(512) else FALLBACK_ICON_SIZE
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }
    }
}
