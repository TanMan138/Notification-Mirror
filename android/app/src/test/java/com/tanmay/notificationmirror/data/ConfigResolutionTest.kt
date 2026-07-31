package com.tanmay.notificationmirror.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigResolutionTest {

    private val stored = SupabaseConfig("https://stored.supabase.co", "sb_publishable_stored")
    private val buildTime = SupabaseConfig("https://buildtime.supabase.co", "sb_publishable_build")

    @Test
    fun `prefers stored configuration`() {
        assertEquals(stored, resolveConfig(stored = stored, buildTime = buildTime))
    }

    @Test
    fun `falls back to build time configuration`() {
        assertEquals(buildTime, resolveConfig(stored = null, buildTime = buildTime))
    }

    @Test
    fun `is unconfigured when neither is present`() {
        assertNull(resolveConfig(stored = null, buildTime = null))
    }

    @Test
    fun `uses stored configuration even when build time is present`() {
        assertEquals(stored, resolveConfig(stored = stored, buildTime = buildTime))
    }
}
