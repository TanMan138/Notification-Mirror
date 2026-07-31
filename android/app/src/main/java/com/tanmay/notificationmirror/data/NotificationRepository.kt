package com.tanmay.notificationmirror.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext

private const val TABLE = "notifications"
private const val CHANNEL = "public:notifications"

interface NotificationRepository {
    suspend fun send(notification: MirroredNotification)

    /** Emits the key of each notification the Mac has cleared. */
    fun observeDismissals(): Flow<String>
}

class SupabaseNotificationRepository(
    private val client: SupabaseClient
) : NotificationRepository {

    override suspend fun send(notification: MirroredNotification) {
        client.postgrest.from(TABLE).insert(notification)
    }

    override fun observeDismissals(): Flow<String> = flow {
        // client.channel() caches by topic, and postgresChangeFlow must be called
        // before joining, so the channel has to be released when collection ends.
        // Android rebinds the listener service, and a stale joined channel would
        // make every later subscription throw.
        val channel = client.channel(CHANNEL)
        try {
            val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = TABLE
            }
            channel.subscribe(blockUntilSubscribed = true)
            emitAll(updates.mapNotNull { dismissedKeyOrNull(it.record) })
        } finally {
            withContext(NonCancellable) {
                client.realtime.removeChannel(channel)
            }
        }
    }
}
