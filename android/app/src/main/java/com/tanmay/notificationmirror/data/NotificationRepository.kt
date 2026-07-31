package com.tanmay.notificationmirror.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull

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
        val channel = client.channel(CHANNEL)
        val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = TABLE
        }
        channel.subscribe(blockUntilSubscribed = true)
        emitAll(updates.mapNotNull { dismissedKeyOrNull(it.record) })
    }
}
