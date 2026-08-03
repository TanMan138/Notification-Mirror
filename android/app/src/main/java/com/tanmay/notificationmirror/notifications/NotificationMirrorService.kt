package com.tanmay.notificationmirror.notifications

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.tanmay.notificationmirror.data.MacAction
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
    private val replyTargets = ReplyRegistry<ReplyTarget>()

    override fun onCreate() {
        super.onCreate()
        // collectLatest tears down the previous subscription when config changes,
        // which also releases the realtime channel.
        serviceScope.launch {
            repositoryFlow.collectLatest { repository ->
                if (repository == null) return@collectLatest
                try {
                    repository.observeMacActions().collect { action ->
                        when (action) {
                            is MacAction.Dismissal -> {
                                cancelNotification(action.key)
                                MirrorLog.d("Cleared ${action.key} from the cloud.")
                            }
                            is MacAction.Reply -> deliverReply(repository, action)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e // Normal teardown when the service is unbound or config changes.
                } catch (e: Exception) {
                    MirrorLog.e("Mac action listener stopped", e)
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // The registry is process memory, and Android rebinds this service freely.
        // getActiveNotifications() is the only way to get the PendingIntents back.
        val fresh = try {
            activeNotifications.orEmpty().mapNotNull { sbn ->
                sbn.notification?.replyTarget()?.let { sbn.key to it }
            }.toMap()
        } catch (e: Exception) {
            MirrorLog.e("Could not rebuild reply targets", e)
            emptyMap()
        }
        replyTargets.replaceAll(fresh)
        MirrorLog.d("Rebuilt ${fresh.size} reply targets.")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return
        val repository = repositoryFlow.value ?: return // Unconfigured: drop it.

        val replyTarget = notification.replyTarget()
        if (replyTarget != null) replyTargets.put(sbn.key, replyTarget)

        val payload = MirroredNotification(
            notificationKey = sbn.key,
            appName = appLabel(sbn.packageName),
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            bodyText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            base64Icon = appIcon(sbn.packageName),
            isRepliable = replyTarget != null
        )

        serviceScope.launch {
            try {
                repository.send(payload)
            } catch (e: Exception) {
                MirrorLog.e("Failed to send notification", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.key?.let { replyTargets.remove(it) }
        super.onNotificationRemoved(sbn)
    }

    /**
     * Fires the posting app's own reply intent. Failure is routine — the
     * notification may already be gone, in which case send() raises
     * CanceledException — so the outcome always goes back to the Mac.
     */
    private suspend fun deliverReply(repository: NotificationRepository, action: MacAction.Reply) {
        val target = replyTargets.get(action.key)
        val delivered = when {
            target == null -> {
                MirrorLog.d("No live reply target for ${action.key}.")
                false
            }
            else -> try {
                target.pendingIntent.send(this, 0, target.fillIn(action.text))
                true
            } catch (e: PendingIntent.CanceledException) {
                MirrorLog.e("Reply intent was already cancelled", e)
                false
            }
        }

        try {
            repository.markReplyResult(action.key, delivered)
        } catch (e: Exception) {
            MirrorLog.e("Could not record the reply outcome", e)
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
