package ru.haykor.sudv

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import ru.haykor.sudv.contract.ApiRoutes
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Matches the defaults in `application.yaml`, which [apiTest] does not override. */
private const val DEFAULT_JWT_ISSUER = "sudv"
private const val DEFAULT_JWT_SECRET = "dev-only-secret-change-me"

class TokenValidationTest {
    @Test
    fun missingTokenIsRejected() = apiTest {
        val response = client.get(ApiRoutes.DEPARTMENTS)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue("\"code\":\"UNAUTHORIZED\"" in response.bodyAsText())
    }

    @Test
    fun garbageTokenIsRejected() = apiTest {
        val response = client.get(ApiRoutes.DEPARTMENTS) { header("Authorization", "Bearer not-a-real-token") }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue("\"code\":\"UNAUTHORIZED\"" in response.bodyAsText())
    }

    @Test
    fun expiredTokenIsRejected() = apiTest {
        val expiredToken = JWT.create()
            .withIssuer(DEFAULT_JWT_ISSUER)
            .withSubject("1")
            .withExpiresAt(Date(System.currentTimeMillis() - 60_000))
            .sign(Algorithm.HMAC256(DEFAULT_JWT_SECRET))

        val response = client.get(ApiRoutes.DEPARTMENTS) { header("Authorization", "Bearer $expiredToken") }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue("\"code\":\"UNAUTHORIZED\"" in response.bodyAsText())
    }
}
