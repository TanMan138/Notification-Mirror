package com.tanmay.notificationmirror.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A row of the `notifications` table, as written by this device. */
@Serializable
data class MirroredNotification(
    @SerialName("notification_key") val notificationKey: String,
    @SerialName("app_name") val appName: String?,
    @SerialName("title") val title: String,
    @SerialName("body_text") val bodyText: String?,
    @SerialName("base64_icon") val base64Icon: String?
)
