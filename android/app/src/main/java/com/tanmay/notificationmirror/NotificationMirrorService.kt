package com.tanmay.notificationmirror

import android.util.Log
import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.tanmay.notificationmirror.notifications.IconCodec
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
            IconCodec.encode(packageManager.getApplicationIcon(sbn.packageName))
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

}
