package com.tanmay.notificationmirror.data

/** Outcome of probing a user's project during setup. */
sealed class ConnectionCheck {
    object Ok : ConnectionCheck()
    object InvalidCredentials : ConnectionCheck()
    object TableMissing : ConnectionCheck()

    /**
     * The table exists but is not streamed, so nothing would ever be delivered.
     * Also covers a setup that predates the status function.
     */
    object RealtimeNotReady : ConnectionCheck()

    /** The table streams, but predates reply support. */
    object SetupOutdated : ConnectionCheck()
    object Unreachable : ConnectionCheck()
    data class Unknown(val detail: String) : ConnectionCheck()

    val userMessage: String
        get() = when (this) {
            Ok -> "Connected."
            InvalidCredentials -> "That key was rejected. Copy the publishable key from Project Settings, API."
            TableMissing -> "Connected, but the notifications table is missing. Run the SQL above first."
            RealtimeNotReady ->
                "Connected, but the table is not set up for realtime, so nothing would be " +
                    "mirrored. Run the SQL above again — it is safe to re-run."
            SetupOutdated ->
                "Connected, but this project was set up before replies were supported. " +
                    "Run the SQL above again — it is safe to re-run."
            Unreachable -> "Could not reach the project. Check the URL and your connection."
            is Unknown -> "Something went wrong: $detail"
        }
}
