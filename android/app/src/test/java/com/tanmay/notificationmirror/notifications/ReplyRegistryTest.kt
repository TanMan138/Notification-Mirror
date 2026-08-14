package com.tanmay.notificationmirror.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplyRegistryTest {

    @Test
    fun `returns a stored target`() {
        val registry = ReplyRegistry<String>()

        registry.put("key", "target")

        assertEquals("target", registry.get("key"))
    }

    @Test
    fun `returns null for a key it never saw`() {
        assertNull(ReplyRegistry<String>().get("key"))
    }

    @Test
    fun `forgets a target once its notification is removed`() {
        val registry = ReplyRegistry<String>()
        registry.put("key", "target")

        registry.remove("key")

        assertNull(registry.get("key"))
    }

    @Test
    fun `replaceAll drops entries the rebuild did not include`() {
        val registry = ReplyRegistry<String>()
        registry.put("stale", "old")

        registry.replaceAll(mapOf("fresh" to "new"))

        assertNull(registry.get("stale"))
        assertEquals("new", registry.get("fresh"))
        assertEquals(1, registry.size())
    }

    @Test
    fun `replaceAll with nothing active empties the registry`() {
        val registry = ReplyRegistry<String>()
        registry.put("stale", "old")

        registry.replaceAll(emptyMap())

        assertEquals(0, registry.size())
    }
}
