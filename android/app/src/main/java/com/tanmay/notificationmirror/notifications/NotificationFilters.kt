package com.tanmay.notificationmirror.notifications

import android.app.Notification

/**
 * True for the collapsed stand-in a messaging app posts alongside the real
 * notification once a second conversation is unread.
 *
 * Mirroring these doubles every banner on the Mac, and the summary is the
 * useless half: it carries no actions, so it has no reply. WhatsApp posts it
 * ~500ms *after* the message, so its banner lands on top of the repliable one
 * and replying becomes impossible. Confirmed from dumpsys:
 *
 *   key=0|com.whatsapp|1|null|10309   flags=SHOW_LIGHTS|GROUP_SUMMARY  (no actions)
 *   key=0|com.whatsapp|1|id215m1b8…   flags=ONLY_ALERT_ONCE  actions=3  MessagingStyle
 */
internal fun isGroupSummary(flags: Int): Boolean =
    flags and Notification.FLAG_GROUP_SUMMARY != 0
