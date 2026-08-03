package com.tanmay.notificationmirror.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupStatusCheckTest {

    @Test
    fun `accepts a fully configured project`() {
        val status = SetupStatus(inPublication = true, replicaIdentityFull = true, replyColumns = true)

        assertEquals(ConnectionCheck.Ok, setupCheck(status))
    }

    @Test
    fun `reports an outdated schema when only the reply columns are missing`() {
        val status = SetupStatus(inPublication = true, replicaIdentityFull = true, replyColumns = false)

        assertEquals(ConnectionCheck.SetupOutdated, setupCheck(status))
    }

    @Test
    fun `prefers the realtime failure when both are wrong`() {
        val status = SetupStatus(inPublication = false, replicaIdentityFull = true, replyColumns = false)

        assertEquals(ConnectionCheck.RealtimeNotReady, setupCheck(status))
    }

    @Test
    fun `treats a missing status row as a setup that predates the function`() {
        assertEquals(ConnectionCheck.RealtimeNotReady, setupCheck(null))
    }
}
