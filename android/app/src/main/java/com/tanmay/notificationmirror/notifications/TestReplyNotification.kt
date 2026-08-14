package com.tanmay.notificationmirror.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat

const val TEST_REPLIABLE_NOTIFICATION_ID = 2

/**
 * Posts a notification shaped like a messaging app's: a MessagingStyle body and
 * a reply action carrying a RemoteInput, flagged SEMANTIC_ACTION_REPLY.
 *
 * This is what [ReplyActions] looks for, so it exercises the Mac round trip end
 * to end without needing anyone to message you.
 */
fun postRepliableTestNotification(context: Context) {
    // Inlined rather than extracted: lint's permission check does not follow
    // the call into a helper, which is why postTestNotification writes it out too.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val remoteInput = RemoteInput.Builder(TEST_REPLY_RESULT_KEY)
        .setLabel("Reply")
        .build()

    val intent = Intent(context, TestReplyReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        intent,
        // Must be mutable: the whole point is that the sender fills in the
        // RemoteInput results before firing it. An immutable one silently
        // arrives with no text.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    val reply = NotificationCompat.Action.Builder(
        android.R.drawable.ic_menu_send,
        "Reply",
        pendingIntent
    )
        .addRemoteInput(remoteInput)
        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
        .setShowsUserInterface(false)
        .build()

    val notification = NotificationCompat.Builder(context, TEST_NOTIFICATION_CHANNEL_ID)
        .setContentTitle("Repliable test")
        .setContentText("Reply to this from the Mac.")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        // A "Mark as read" action placed first, with no RemoteInput, so the
        // action picker is exercised rather than trivially picking action zero.
        .addAction(
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_view,
                "Mark as read",
                pendingIntent
            ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).build()
        )
        .addAction(reply)
        .build()

    NotificationManagerCompat.from(context).notify(TEST_REPLIABLE_NOTIFICATION_ID, notification)
}
