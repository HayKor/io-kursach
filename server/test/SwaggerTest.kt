package ru.haykor.sudv

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwaggerTest {
    @Test
    fun specContainsRoutes() = apiTest {
        val response = client.get("/docs/documentation.yaml")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue("\"/health\"" in response.bodyAsText())
    }
}
