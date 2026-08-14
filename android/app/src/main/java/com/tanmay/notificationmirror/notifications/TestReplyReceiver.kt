package com.tanmay.notificationmirror.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.tanmay.notificationmirror.util.MirrorLog

const val TEST_REPLY_RESULT_KEY = "notification_mirror_test_reply"
const val TEST_NOTIFICATION_CHANNEL_ID = "notification_mirror_test"

private const val TEST_REPLY_ECHO_ID = 3

/**
 * Catches the reply sent to the repliable test notification, standing in for
 * WhatsApp so the whole Mac round trip can be exercised without a second
 * person. Echoes the text back as its own notification, which mirrors to the
 * Mac in turn — so a successful round trip is visible on both devices.
 */
class TestReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(TEST_REPLY_RESULT_KEY)
            ?.toString()

        if (text.isNullOrBlank()) {
            MirrorLog.e("Test reply arrived with no text.", IllegalStateException("empty RemoteInput"))
            return
        }
        MirrorLog.d("Test reply received: $text")

        // Clearing it mimics a messaging app retiring the notification once the
        // reply lands, which is also what makes a second reply to the same one fail.
        context.getSystemService<NotificationManager>()?.cancel(TEST_REPLIABLE_NOTIFICATION_ID)

        // Inlined rather than extracted: lint's permission check does not follow
        // the call into a helper.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val echo = NotificationCompat.Builder(context, TEST_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Reply received")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(TEST_REPLY_ECHO_ID, echo)
    }
}
