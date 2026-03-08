package com.tanmay.notificationmirror

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class NotificationMirrorService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var dismissServer: DismissServer? = null

    override fun onCreate() {
        super.onCreate()
        dismissServer = DismissServer { key -> handleDismissRequest(key) }.apply {
            try {
                start()
            } catch (_: Exception) {
                // Port may be in use
            }
        }
    }

    override fun onDestroy() {
        try {
            dismissServer?.stop()
        } catch (_: Exception) { }
        dismissServer = null
        super.onDestroy()
    }

    private fun handleDismissRequest(key: String) {
        try {
            cancelNotification(key)
        } catch (_: Exception) { }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val ip = prefs.getString(KEY_TARGET_IP, DEFAULT_IP) ?: DEFAULT_IP

        if (ip.isBlank()) return

        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (_: Exception) {
            null
        }

        val appIconBase64 = try {
            val icon = packageManager.getApplicationIcon(sbn.packageName)
            drawableToBase64Png(icon)
        } catch (_: Exception) {
            null
        }

        val notificationKey = sbn.key
        val phoneIp = getLocalIpAddress()

        serviceScope.launch {
            try {
                NetworkManager.sendNotification(
                    ip = ip,
                    title = title,
                    text = text,
                    appName = appName,
                    appIconBase64 = appIconBase64,
                    notificationKey = notificationKey,
                    phoneIp = phoneIp
                )
            } catch (_: Exception) {
                // Fire-and-forget; optionally log
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "notification_mirror_prefs"
        private const val KEY_TARGET_IP = "target_ip"
        private const val DEFAULT_IP = ""  // User's Mac IP; set via prefs from MainActivity
        private const val FALLBACK_ICON_SIZE = 96

        /**
         * Converts a [Drawable] to a PNG-compressed Base64 string.
         */
        fun drawableToBase64Png(drawable: Drawable): String? {
            val bitmap = drawableToBitmap(drawable) ?: return null
            val output = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) return null
            return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
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
