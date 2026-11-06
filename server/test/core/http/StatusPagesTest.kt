package ru.haykor.sudv.server.core.http

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import ru.haykor.sudv.server.apiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusPagesTest {
    @Test
    fun unknownExceptionBecomesInternalErrorWithoutStacktrace() = apiTest(
        // The route only exists for this test; production code never throws a bare exception on purpose.
        beforeStart = {
            routing {
                get("/test-unhandled-failure") { error("something went very wrong") }
            }
        },
    ) {
        val response = client.get("/test-unhandled-failure")
        assertEquals(HttpStatusCode.InternalServerError, response.status)

        val body = response.bodyAsText()
        assertTrue("\"code\":\"INTERNAL_ERROR\"" in body)
        assertFalse("something went very wrong" in body)
        assertFalse("IllegalStateException" in body)
        assertFalse("\tat " in body)
    }
}
