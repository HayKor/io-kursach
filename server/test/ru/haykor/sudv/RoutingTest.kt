package ru.haykor.sudv

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutingTest {
    @Test
    fun rootReturnsModelDto() = testApplication {
        configure("application.yaml")
        val client = createClient {
            install(ContentNegotiation) { json() }
        }
        assertEquals(ModelDto(id = 1), client.get("/").body<ModelDto>())
    }
}
