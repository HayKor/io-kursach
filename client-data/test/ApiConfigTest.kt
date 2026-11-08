package ru.haykor.sudv.client.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiConfigTest {
    @Test
    fun configuredAddressWins() {
        assertEquals("http://sudv.local:8080", ApiConfig.of(" http://sudv.local:8080/ ", "http://page").baseUrl)
    }

    @Test
    fun blankOrMissingFallsBack() {
        assertEquals("http://page", ApiConfig.of("", "http://page").baseUrl)
        assertEquals("http://page", ApiConfig.of("   ", "http://page").baseUrl)
        assertEquals("http://page", ApiConfig.of(null, "http://page").baseUrl)
    }
}
