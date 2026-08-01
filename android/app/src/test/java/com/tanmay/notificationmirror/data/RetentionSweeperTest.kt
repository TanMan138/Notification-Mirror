package com.tanmay.notificationmirror.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionSweeperTest {

    private val now = 1_753_939_200L

    @Test
    fun `sweeps when it has never run`() {
        assertTrue(shouldSweep(lastRunEpochSeconds = null, nowEpochSeconds = now))
    }

    @Test
    fun `sweeps when the last run was more than a day ago`() {
        assertTrue(shouldSweep(now - 90_000, now))
    }

    @Test
    fun `skips when the last run was an hour ago`() {
        assertFalse(shouldSweep(now - 3_600, now))
    }

    @Test
    fun `sweeps when the clock has moved backwards`() {
        assertTrue(shouldSweep(now + 10_000, now))
    }
}
