package com.tanmay.notificationmirror.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

private const val PAIRING_VERSION = 1
private const val MAX_AGE_SECONDS = 300L

/** Tolerates a little clock skew between the two devices. */
private const val MAX_SKEW_SECONDS = 60L

sealed class PairingError : Exception() {
    object Malformed : PairingError()
    object UnsupportedVersion : PairingError()
    object Expired : PairingError()
}

@Serializable
private data class PairingWire(
    @SerialName("v") val version: Int,
    @SerialName("url") val url: String,
    @SerialName("key") val key: String,
    @SerialName("iat") val issuedAt: Long
)

private val json = Json { encodeDefaults = true }

fun encodePairingPayload(config: SupabaseConfig, issuedAtEpochSeconds: Long): String {
    val wire = PairingWire(PAIRING_VERSION, config.url, config.publishableKey, issuedAtEpochSeconds)
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(json.encodeToString(wire).toByteArray(Charsets.UTF_8))
}

fun decodePairingPayload(encoded: String, nowEpochSeconds: Long): Result<SupabaseConfig> {
    val wire = try {
        val bytes = Base64.getUrlDecoder().decode(encoded.trim())
        json.decodeFromString<PairingWire>(String(bytes, Charsets.UTF_8))
    } catch (_: Exception) {
        return Result.failure(PairingError.Malformed)
    }

    if (wire.version != PAIRING_VERSION) return Result.failure(PairingError.UnsupportedVersion)

    val age = nowEpochSeconds - wire.issuedAt
    if (age > MAX_AGE_SECONDS || age < -MAX_SKEW_SECONDS) return Result.failure(PairingError.Expired)

    return parseSupabaseConfig(wire.url, wire.key)
}
