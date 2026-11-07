package ru.haykor.sudv.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class DeepLinkTest {
    @Test
    fun pathSegmentsOfAFullUrl() {
        assertEquals(listOf("penalties", "42"), DeepLink.parse("http://localhost:8081/penalties/42?x=1#top").segments)
    }

    @Test
    fun rootOrNothingIsNone() {
        assertEquals(DeepLink.None, DeepLink.parse("http://localhost:8081/"))
        assertEquals(DeepLink.None, DeepLink.parse("http://localhost:8081"))
        assertEquals(DeepLink.None, DeepLink.parse(null))
    }

    @Test
    fun eachLevelTakesItsOwnSegment() {
        val link = DeepLink.parse("/employees/7/card")
        assertEquals("employees", link.first)
        assertEquals(listOf("7", "card"), link.dropFirst().segments)
    }
}
