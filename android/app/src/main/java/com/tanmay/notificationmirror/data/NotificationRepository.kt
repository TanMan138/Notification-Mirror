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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val TABLE = "notifications"
private const val CHANNEL = "public:notifications"
private const val STATUS_FUNCTION = "mirror_setup_status"

@Serializable
internal data class SetupStatus(
    @SerialName("in_publication") val inPublication: Boolean,
    @SerialName("replica_identity_full") val replicaIdentityFull: Boolean,
    // A project still running the two-column function decodes with false, which
    // reports SetupOutdated — the fix is the same re-run either way.
    @SerialName("reply_columns") val replyColumns: Boolean = false
)

/** Turns a status row into a verdict. Null means the function is missing. */
internal fun setupCheck(status: SetupStatus?): ConnectionCheck = when {
    status == null || !status.inPublication || !status.replicaIdentityFull -> ConnectionCheck.RealtimeNotReady
    !status.replyColumns -> ConnectionCheck.SetupOutdated
    else -> ConnectionCheck.Ok
}

interface NotificationRepository {
    suspend fun send(notification: MirroredNotification)

    /** Emits each thing the Mac asked this phone to do. */
    fun observeMacActions(): Flow<MacAction>

    /** Records whether a reply reached the app that posted the notification. */
    suspend fun markReplyResult(notificationKey: String, delivered: Boolean)

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

    override fun observeMacActions(): Flow<MacAction> = flow {
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
            emitAll(updates.mapNotNull { macActionOrNull(it.record) })
        } finally {
            withContext(NonCancellable) {
                client.realtime.removeChannel(channel)
            }
        }
    }

    override suspend fun markReplyResult(notificationKey: String, delivered: Boolean) {
        client.postgrest.from(TABLE).update({
            set("reply_status", if (delivered) "sent" else "failed")
        }) {
            filter { eq("notification_key", notificationKey) }
        }
    }

    override suspend fun checkConnection(): ConnectionCheck = try {
        client.postgrest.from(TABLE).select { limit(1) }
        // Reaching the table proves nothing about delivery: a table outside the
        // realtime publication reads fine and streams nothing.
        checkRealtimeReady()
    } catch (e: Exception) {
        classifyConnectionFailure(e)
    }

    private suspend fun checkRealtimeReady(): ConnectionCheck = try {
        setupCheck(client.postgrest.rpc(STATUS_FUNCTION).decodeList<SetupStatus>().firstOrNull())
    } catch (_: Exception) {
        // Missing function means the project was set up from older SQL.
        ConnectionCheck.RealtimeNotReady
    }

    override suspend fun purgeOlderThan(hours: Int) {
        val cutoff = Instant.now().minus(hours.toLong(), ChronoUnit.HOURS)
        client.postgrest.from(TABLE).delete {
            filter { lt("created_at", cutoff.toString()) }
        }
    }
}

/**
 * Maps a probe failure onto something a user can act on. The matched substrings
 * were taken from real supabase-kt 3.1.4 failures:
 *  - "Invalid API key (Double check your API key.)"
 *  - "Could not find the table 'public.notifications' in the schema cache"
 *  - "Unable to resolve host ...: No address associated with hostname"
 */
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
        else -> ConnectionCheck.Unknown(safeDetail(e))
    }
}

/**
 * Postgrest failures dump every request header, including the API key, into
 * the message. Only the first line is safe to show or log.
 */
private fun safeDetail(e: Exception): String =
    e.message?.lineSequence()?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        ?: e::class.java.simpleName
