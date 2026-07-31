package com.tanmay.notificationmirror.data

/** Outcome of probing a user's project during setup. */
sealed class ConnectionCheck {
    object Ok : ConnectionCheck()
    object InvalidCredentials : ConnectionCheck()
    object TableMissing : ConnectionCheck()
    object Unreachable : ConnectionCheck()
    data class Unknown(val detail: String) : ConnectionCheck()

    val userMessage: String
        get() = when (this) {
            Ok -> "Connected."
            InvalidCredentials -> "That key was rejected. Copy the publishable key from Project Settings, API."
            TableMissing -> "Connected, but the notifications table is missing. Run the SQL above first."
            Unreachable -> "Could not reach the project. Check the URL and your connection."
            is Unknown -> "Something went wrong: $detail"
        }
}
