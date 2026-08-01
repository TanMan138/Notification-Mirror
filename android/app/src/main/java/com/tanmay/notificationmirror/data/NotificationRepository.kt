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
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val TABLE = "notifications"
private const val CHANNEL = "public:notifications"

interface NotificationRepository {
    suspend fun send(notification: MirroredNotification)

    /** Emits the key of each notification the Mac has cleared. */
    fun observeDismissals(): Flow<String>

    /** Probes the project during setup. Never throws. */
    suspend fun checkConnection(): ConnectionCheck

    /** Deletes this project's rows older than [hours]. */
    suspend fun purgeOlderThan(hours: Int)
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

    override suspend fun checkConnection(): ConnectionCheck = try {
        client.postgrest.from(TABLE).select { limit(1) }
        ConnectionCheck.Ok
    } catch (e: Exception) {
        classifyConnectionFailure(e)
    }

    override suspend fun purgeOlderThan(hours: Int) {
        val cutoff = Instant.now().minus(hours.toLong(), ChronoUnit.HOURS)
        client.postgrest.from(TABLE).delete {
            filter { lt("created_at", cutoff.toString()) }
        }
    }
}

/** Maps a probe failure onto something a user can act on. */
internal fun classifyConnectionFailure(e: Exception): ConnectionCheck {
    val message = (e.message ?: "").lowercase()
    return when {
        "pgrst205" in message || "could not find the table" in message -> ConnectionCheck.TableMissing
        "401" in message || "403" in message ||
            "invalid api key" in message || "jwt" in message -> ConnectionCheck.InvalidCredentials
        e is java.io.IOException ||
            "unable to resolve host" in message ||
            "failed to connect" in message ||
            "timeout" in message -> ConnectionCheck.Unreachable
        else -> ConnectionCheck.Unknown(e.message ?: e::class.java.simpleName)
    }
}
