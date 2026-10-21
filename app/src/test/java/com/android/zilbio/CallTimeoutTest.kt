package com.android.zilbio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallTimeoutTest {

    companion object {
        const val RINGING_TIMEOUT_MS = 30_000L
    }

    @Test
    fun testRingingTimeoutCap() {
        assertEquals(30_000L, RINGING_TIMEOUT_MS)

        val callStartTime = 1_000_000L
        val activeCallTime = callStartTime + 25_000L
        val timedOutCallTime = callStartTime + 30_001L

        assertFalse(activeCallTime - callStartTime >= RINGING_TIMEOUT_MS)
        assertTrue(timedOutCallTime - callStartTime >= RINGING_TIMEOUT_MS)
    }
}
