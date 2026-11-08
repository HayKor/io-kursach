package ru.haykor.sudv.server.core.http

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import ru.haykor.sudv.server.apiTest
import ru.haykor.sudv.shared.contract.ApiRoutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val ALLOWED_ORIGIN = "http://web.test:8081"

private val corsConfig = mapOf("cors.allowedOrigins" to "http://other.test, $ALLOWED_ORIGIN/")

class CorsTest {
    @Test
    fun preflightFromAllowedOriginPasses() = apiTest(config = corsConfig) {
        val response = client.options(ApiRoutes.EMPLOYEES) {
            header(HttpHeaders.Origin, ALLOWED_ORIGIN)
            header(HttpHeaders.AccessControlRequestMethod, "PUT")
            header(HttpHeaders.AccessControlRequestHeaders, "authorization, content-type")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ALLOWED_ORIGIN, response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun errorResponseIsReadableByAllowedOrigin() = apiTest(config = corsConfig) {
        val response = client.get(ApiRoutes.DEPARTMENTS) { header(HttpHeaders.Origin, ALLOWED_ORIGIN) }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(ALLOWED_ORIGIN, response.headers[HttpHeaders.AccessControlAllowOrigin])
        val exposed = response.headers[HttpHeaders.AccessControlExposeHeaders].orEmpty()
        assertTrue(HttpHeaders.ContentDisposition in exposed, exposed)
    }

    @Test
    fun unknownOriginIsRejected() = apiTest(config = corsConfig) {
        val response = client.options(ApiRoutes.EMPLOYEES) {
            header(HttpHeaders.Origin, "http://evil.test")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
    }
}
