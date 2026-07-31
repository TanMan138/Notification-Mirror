package com.tanmay.notificationmirror.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PairingPayloadTest {

    private val config = SupabaseConfig(
        url = "https://abcdefgh.supabase.co",
        publishableKey = "sb_publishable_abcdefghijklmnop"
    )
    private val issuedAt = 1_753_939_200L

    /** Shared with the macOS suite. Changing this is a breaking format change. */
    private val fixedVector =
        "eyJ2IjoxLCJ1cmwiOiJodHRwczovL2FiY2RlZmdoLnN1cGFiYXNlLmNvIiwia2V5Ijoic2JfcHVibGlzaGFibGVfYWJjZGVmZ2hpamtsbW5vcCIsImlhdCI6MTc1MzkzOTIwMH0"

    @Test
    fun `encodes to the agreed wire format`() {
        assertEquals(fixedVector, encodePairingPayload(config, issuedAt))
    }

    @Test
    fun `round trips`() {
        val encoded = encodePairingPayload(config, issuedAt)
        val decoded = decodePairingPayload(encoded, nowEpochSeconds = issuedAt + 10).getOrThrow()

        assertEquals(config, decoded)
    }

    @Test
    fun `accepts a payload four minutes old`() {
        val encoded = encodePairingPayload(config, issuedAt)

        assertEquals(config, decodePairingPayload(encoded, issuedAt + 240).getOrThrow())
    }

    @Test
    fun `rejects a payload six minutes old`() {
        val encoded = encodePairingPayload(config, issuedAt)

        assertEquals(
            PairingError.Expired,
            decodePairingPayload(encoded, issuedAt + 360).exceptionOrNull()
        )
    }

    @Test
    fun `rejects a payload issued in the future beyond tolerance`() {
        val encoded = encodePairingPayload(config, issuedAt)

        assertEquals(
            PairingError.Expired,
            decodePairingPayload(encoded, issuedAt - 120).exceptionOrNull()
        )
    }

    @Test
    fun `rejects an unknown version`() {
        val v2 = encodePairingPayload(config, issuedAt).let {
            val json = String(java.util.Base64.getUrlDecoder().decode(it))
            java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.replace("\"v\":1", "\"v\":2").toByteArray())
        }

        assertEquals(PairingError.UnsupportedVersion, decodePairingPayload(v2, issuedAt).exceptionOrNull())
    }

    @Test
    fun `rejects a string that is not base64`() {
        assertEquals(
            PairingError.Malformed,
            decodePairingPayload("not a pairing string!!", issuedAt).exceptionOrNull()
        )
    }

    @Test
    fun `rejects a truncated payload`() {
        val truncated = encodePairingPayload(config, issuedAt).dropLast(20)

        assertEquals(PairingError.Malformed, decodePairingPayload(truncated, issuedAt).exceptionOrNull())
    }

    @Test
    fun `rejects a payload carrying a service role key`() {
        val serviceRoleJwt =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJyb2xlIjoic2VydmljZV9yb2xlIiwicmVmIjoiYWJjIn0." +
                "c2lnbmF0dXJl"
        val encoded = encodePairingPayload(config.copy(publishableKey = serviceRoleJwt), issuedAt)

        assertEquals(ConfigError.KeyIsSecret, decodePairingPayload(encoded, issuedAt).exceptionOrNull())
    }
}
