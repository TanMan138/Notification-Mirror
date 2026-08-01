package com.tanmay.notificationmirror.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException

class ConnectionFailureTest {

    @Test
    fun `recognises a rejected key`() {
        val e = Exception("Invalid API key (Double check your API key.)")
        assertEquals(ConnectionCheck.InvalidCredentials, classifyConnectionFailure(e))
    }

    @Test
    fun `recognises a missing table`() {
        val e = Exception("Could not find the table 'public.notifications' in the schema cache")
        assertEquals(ConnectionCheck.TableMissing, classifyConnectionFailure(e))
    }

    @Test
    fun `recognises an unresolvable host`() {
        val e = Exception("Unable to resolve host \"x.supabase.co\": No address associated with hostname")
        assertEquals(ConnectionCheck.Unreachable, classifyConnectionFailure(e))
    }

    @Test
    fun `treats any IOException as unreachable`() {
        assertEquals(ConnectionCheck.Unreachable, classifyConnectionFailure(IOException("socket closed")))
    }

    @Test
    fun `never surfaces the api key from a header dump`() {
        val e = Exception(
            "Something unmapped\n" +
                "URL: https://ref.supabase.co/rest/v1/notifications\n" +
                "Headers: [apikey=[sb_publishable_SECRETVALUE]]"
        )
        val result = classifyConnectionFailure(e) as ConnectionCheck.Unknown

        assertEquals("Something unmapped", result.detail)
        assertFalse(result.userMessage.contains("sb_publishable_SECRETVALUE"))
    }
}
