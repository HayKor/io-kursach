package ru.haykor.sudv.server.core.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import ru.haykor.sudv.server.apiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutingTest {
    @Test
    fun healthReturnsOk() = apiTest {
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("OK", response.bodyAsText())
    }
}
