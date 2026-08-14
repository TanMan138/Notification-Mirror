package com.tanmay.notificationmirror.data

import com.tanmay.notificationmirror.util.MirrorLog

private const val SWEEP_INTERVAL_SECONDS = 86_400L
private const val RETENTION_HOURS = 24
private const val LAST_SWEEP_KEY = "last_sweep"

/** True when a day has passed, or the stored timestamp is missing or in the future. */
fun shouldSweep(lastRunEpochSeconds: Long?, nowEpochSeconds: Long): Boolean {
    if (lastRunEpochSeconds == null) return true
    if (lastRunEpochSeconds > nowEpochSeconds) return true
    return nowEpochSeconds - lastRunEpochSeconds >= SWEEP_INTERVAL_SECONDS
}

class RetentionSweeper(private val secureStore: SecureStore) {

    suspend fun sweepIfDue(repository: NotificationRepository, nowEpochSeconds: Long) {
        val last = secureStore.get(LAST_SWEEP_KEY)?.toLongOrNull()
        if (!shouldSweep(last, nowEpochSeconds)) return

        try {
            repository.purgeOlderThan(RETENTION_HOURS)
            secureStore.put(LAST_SWEEP_KEY, nowEpochSeconds.toString())
        } catch (e: Exception) {
            // Expected occasionally: the pg_cron job deletes the same expired
            // rows hourly, and two concurrent deletes can deadlock. Both sides
            // retry on their own schedule, so a lost sweep costs nothing. This
            // sweeper stays because a project whose pg_cron extension failed to
            // enable has no other cleanup at all.
            MirrorLog.e("Retention sweep failed", e)
        }
    }
}
