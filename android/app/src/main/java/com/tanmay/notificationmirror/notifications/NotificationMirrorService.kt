package com.tanmay.notificationmirror.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.tanmay.notificationmirror.data.MirroredNotification
import com.tanmay.notificationmirror.data.NotificationRepository
import com.tanmay.notificationmirror.mirrorApp
import com.tanmay.notificationmirror.util.MirrorLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NotificationMirrorService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repositoryFlow: StateFlow<NotificationRepository?> by lazy { mirrorApp.repositoryFlow }

    override fun onCreate() {
        super.onCreate()
        // collectLatest tears down the previous subscription when config changes,
        // which also releases the realtime channel.
        serviceScope.launch {
            repositoryFlow.collectLatest { repository ->
                if (repository == null) return@collectLatest
                try {
                    repository.observeDismissals().collect { key ->
                        cancelNotification(key)
                        MirrorLog.d("Cleared $key from the cloud.")
                    }
                } catch (e: CancellationException) {
                    throw e // Normal teardown when the service is unbound or config changes.
                } catch (e: Exception) {
                    MirrorLog.e("Dismiss listener stopped", e)
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return
        val repository = repositoryFlow.value ?: return // Unconfigured: drop it.

        val payload = MirroredNotification(
            notificationKey = sbn.key,
            appName = appLabel(sbn.packageName),
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            bodyText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            base64Icon = appIcon(sbn.packageName)
        )

        serviceScope.launch {
            try {
                repository.send(payload)
            } catch (e: Exception) {
                MirrorLog.e("Failed to send notification", e)
            }
        }
    }

    private fun appLabel(packageName: String): String? = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (_: Exception) {
        null
    }

    private fun appIcon(packageName: String): String? = try {
        IconCodec.encode(packageManager.getApplicationIcon(packageName))
    } catch (_: Exception) {
        null
    }
}
