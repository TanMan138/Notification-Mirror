package com.tanmay.notificationmirror.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseConfigTest {

    private val validKey = "sb_publishable_abcdefghijklmnop"

    private fun errorOf(url: String, key: String): ConfigError? =
        parseSupabaseConfig(url, key).exceptionOrNull() as? ConfigError

    @Test
    fun `accepts a well formed project url and key`() {
        val config = parseSupabaseConfig("https://abcdefgh.supabase.co", validKey).getOrThrow()

        assertEquals("https://abcdefgh.supabase.co", config.url)
        assertEquals(validKey, config.publishableKey)
    }

    @Test
    fun `trims surrounding whitespace`() {
        val config = parseSupabaseConfig("  https://abcdefgh.supabase.co  ", "  $validKey  ").getOrThrow()

        assertEquals("https://abcdefgh.supabase.co", config.url)
        assertEquals(validKey, config.publishableKey)
    }

    @Test
    fun `strips a trailing slash`() {
        val config = parseSupabaseConfig("https://abcdefgh.supabase.co/", validKey).getOrThrow()

        assertEquals("https://abcdefgh.supabase.co", config.url)
    }

    @Test
    fun `rejects a blank url`() {
        assertEquals(ConfigError.UrlBlank, errorOf("   ", validKey))
    }

    @Test
    fun `rejects plain http`() {
        assertEquals(ConfigError.UrlNotHttps, errorOf("http://abcdefgh.supabase.co", validKey))
    }

    @Test
    fun `rejects a url with no scheme`() {
        assertEquals(ConfigError.UrlNotHttps, errorOf("abcdefgh.supabase.co", validKey))
    }

    @Test
    fun `rejects a host that is not a supabase project`() {
        assertEquals(ConfigError.UrlNotSupabase, errorOf("https://example.com", validKey))
    }

    @Test
    fun `rejects a blank key`() {
        assertEquals(ConfigError.KeyBlank, errorOf("https://abcdefgh.supabase.co", "  "))
    }

    @Test
    fun `rejects a new format secret key`() {
        assertEquals(
            ConfigError.KeyIsSecret,
            errorOf("https://abcdefgh.supabase.co", "sb_secret_abcdefghijklmnop")
        )
    }

    @Test
    fun `rejects a service role jwt`() {
        // {"alg":"HS256","typ":"JWT"} . {"role":"service_role","ref":"abc"} . signature
        val serviceRoleJwt =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJyb2xlIjoic2VydmljZV9yb2xlIiwicmVmIjoiYWJjIn0." +
                "c2lnbmF0dXJl"

        assertEquals(
            ConfigError.KeyIsSecret,
            errorOf("https://abcdefgh.supabase.co", serviceRoleJwt)
        )
    }

    @Test
    fun `accepts a legacy anon jwt`() {
        // {"alg":"HS256","typ":"JWT"} . {"role":"anon","ref":"abc"} . signature
        val anonJwt =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJyb2xlIjoiYW5vbiIsInJlZiI6ImFiYyJ9." +
                "c2lnbmF0dXJl"

        assertTrue(parseSupabaseConfig("https://abcdefgh.supabase.co", anonJwt).isSuccess)
    }
}
