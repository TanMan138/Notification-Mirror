package com.tanmay.notificationmirror.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconCodecTest {

    @Test
    fun `leaves an icon smaller than the max edge untouched`() {
        assertEquals(32 to 32, IconCodec.computeScaledSize(32, 32, 64))
    }

    @Test
    fun `leaves an icon exactly at the max edge untouched`() {
        assertEquals(64 to 64, IconCodec.computeScaledSize(64, 64, 64))
    }

    @Test
    fun `scales a wide icon so its long edge fits`() {
        assertEquals(64 to 32, IconCodec.computeScaledSize(512, 256, 64))
    }

    @Test
    fun `scales a tall icon so its long edge fits`() {
        assertEquals(32 to 64, IconCodec.computeScaledSize(256, 512, 64))
    }

    @Test
    fun `never produces a dimension below one pixel`() {
        assertEquals(64 to 1, IconCodec.computeScaledSize(1000, 1, 64))
    }

    @Test
    fun `rejects a zero dimension`() {
        assertNull(IconCodec.computeScaledSize(0, 10, 64))
    }

    @Test
    fun `rejects a negative dimension`() {
        assertNull(IconCodec.computeScaledSize(-5, 10, 64))
    }
}
