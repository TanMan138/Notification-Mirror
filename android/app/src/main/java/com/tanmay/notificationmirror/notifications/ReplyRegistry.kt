package com.tanmay.notificationmirror.notifications

/**
 * Live reply handles, keyed on StatusBarNotification.key.
 *
 * Deliberately in-memory only: a PendingIntent cannot be persisted or sent
 * over the wire. Android rebinds the listener service freely, so the service
 * rebuilds this from getActiveNotifications() on every reconnect. Keying on
 * sbn.key rather than a generated id is what makes that rebuild possible.
 *
 * Written from the listener callbacks and read from a coroutine, so every
 * accessor is synchronized.
 */
internal class ReplyRegistry<T : Any> {

    private val entries = HashMap<String, T>()

    @Synchronized
    fun put(key: String, value: T) {
        entries[key] = value
    }

    @Synchronized
    fun remove(key: String) {
        entries.remove(key)
    }

    @Synchronized
    fun get(key: String): T? = entries[key]

    @Synchronized
    fun replaceAll(fresh: Map<String, T>) {
        entries.clear()
        entries.putAll(fresh)
    }

    @Synchronized
    fun size(): Int = entries.size
}
