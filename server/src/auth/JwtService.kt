package ru.haykor.sudv.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.server.auth.jwt.JWTCredential
import ru.haykor.sudv.config.AppConfig
import ru.haykor.sudv.domain.UserId
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@Inject
@SingleIn(AppScope::class)
class JwtService(config: AppConfig, private val clock: Clock) {
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)
    private val issuer = config.jwtIssuer
    private val ttl = config.jwtTtlHours.hours

    /** Used to configure the `jwt` authentication provider's verifier. */
    val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(issuer).build()

    fun issue(id: UserId): String {
        val now = clock.now()
        return JWT.create()
            .withIssuer(issuer)
            .withSubject(id.value.toString())
            .withIssuedAt(Date(now.toEpochMilliseconds()))
            .withExpiresAt(Date((now + ttl).toEpochMilliseconds()))
            .sign(algorithm)
    }

    fun userId(credential: JWTCredential): UserId? =
        credential.payload.subject?.toLongOrNull()?.let { UserId(it) }
}
