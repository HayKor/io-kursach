package ru.haykor.sudv.server.core.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.haykor.sudv.server.apiTest
import ru.haykor.sudv.shared.contract.ApiRoutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwaggerTest {
    @Test
    fun everyOperationIsTaggedByItsRouteGroup() = apiTest {
        val response = client.get("/docs/documentation.yaml")
        assertEquals(HttpStatusCode.OK, response.status)

        val paths = Json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("paths").jsonObject
        val tagsByOperation = paths.flatMap { (path, operations) ->
            operations.jsonObject.map { (method, operation) ->
                "${method.uppercase()} $path" to operation.jsonObject["tags"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            }
        }.toMap()

        val notSingleTagged = tagsByOperation.filterValues { it.size != 1 }
        assertTrue(notSingleTagged.isEmpty(), "Operations without exactly one Swagger tag: $notSingleTagged")

        val expected = mapOf(
            "GET /health" to "Health",
            "POST ${ApiRoutes.LOGIN}" to "Auth",
            "GET ${ApiRoutes.ME}" to "Auth",
            "GET ${ApiRoutes.USERS}" to "Users",
            "GET ${ApiRoutes.DEPARTMENTS}" to "Departments",
            "GET ${ApiRoutes.EMPLOYEES}" to "Employees",
            "GET ${ApiRoutes.PENALTIES}" to "Penalties",
            "POST ${ApiRoutes.PENALTY_ATTACHMENTS}" to "Attachments",
            "GET ${ApiRoutes.REPORTS}" to "Reports",
        )
        expected.forEach { (operation, tag) ->
            assertEquals(listOf(tag), tagsByOperation[operation], operation)
        }
    }
}
