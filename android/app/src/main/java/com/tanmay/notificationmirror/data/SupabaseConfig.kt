package com.tanmay.notificationmirror.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64

/** Credentials for the user's own Supabase project. */
data class SupabaseConfig(
    val url: String,
    val publishableKey: String
)

sealed class ConfigError : Exception() {
    object UrlBlank : ConfigError()
    object UrlNotHttps : ConfigError()
    object UrlNotSupabase : ConfigError()
    object KeyBlank : ConfigError()

    /** A service-role key bypasses row level security and must never ship in a client. */
    object KeyIsSecret : ConfigError()
}

private const val SUPABASE_HOST_SUFFIX = ".supabase.co"

fun parseSupabaseConfig(url: String, key: String): Result<SupabaseConfig> {
    val cleanUrl = url.trim().trimEnd('/')
    val cleanKey = key.trim()

    if (cleanUrl.isBlank()) return Result.failure(ConfigError.UrlBlank)
    if (!cleanUrl.startsWith("https://")) return Result.failure(ConfigError.UrlNotHttps)

    val host = cleanUrl.removePrefix("https://").substringBefore('/')
    if (!host.endsWith(SUPABASE_HOST_SUFFIX)) return Result.failure(ConfigError.UrlNotSupabase)

    if (cleanKey.isBlank()) return Result.failure(ConfigError.KeyBlank)
    if (isSecretKey(cleanKey)) return Result.failure(ConfigError.KeyIsSecret)

    return Result.success(SupabaseConfig(cleanUrl, cleanKey))
}

/** Catches the common mistake of pasting the service-role key from the dashboard. */
internal fun isSecretKey(key: String): Boolean {
    if (key.startsWith("sb_secret_")) return true
    return jwtRole(key) == "service_role"
}

// Reads the top-level claim only. The macOS parser must agree exactly, or a key
// rejected on one platform would be accepted on the other.
private fun jwtRole(token: String): String? {
    val parts = token.split(".")
    if (parts.size != 3) return null
    return try {
        val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        val claims = Json.parseToJsonElement(payload) as? JsonObject ?: return null
        (claims["role"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    } catch (_: Exception) {
        null
    }
}
