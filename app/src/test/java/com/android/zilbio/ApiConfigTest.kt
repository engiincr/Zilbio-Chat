package com.android.zilbio

import com.android.zilbio.config.ApiConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiConfigTest {

    @Test
    fun testApiConfigConstants() {
        assertEquals("free.expressturn.com:3478", ApiConfig.TURN_HOST)
        assertEquals("gemini-1.5-flash", ApiConfig.GEMINI_MODEL)
        assertEquals("gemini-2.0-flash", ApiConfig.GEMINI_FALLBACK_MODEL)
        assertTrue(ApiConfig.GEMINI_BASE_URL.startsWith("https://generativelanguage.googleapis.com"))
    }
}
