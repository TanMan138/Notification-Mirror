package com.tanmay.notificationmirror.notifications

import android.app.Notification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupSummaryTest {

    @Test
    fun `spots the summary WhatsApp posts alongside each message`() {
        // Taken from dumpsys: flags=SHOW_LIGHTS|GROUP_SUMMARY on the null-tag row.
        val flags = Notification.FLAG_SHOW_LIGHTS or Notification.FLAG_GROUP_SUMMARY

        assertTrue(isGroupSummary(flags))
    }

    @Test
    fun `leaves the message itself alone`() {
        // The tagged row carrying the reply action: flags=ONLY_ALERT_ONCE.
        assertFalse(isGroupSummary(Notification.FLAG_ONLY_ALERT_ONCE))
    }

    @Test
    fun `treats a notification with no flags as mirrorable`() {
        assertFalse(isGroupSummary(0))
    }
}
