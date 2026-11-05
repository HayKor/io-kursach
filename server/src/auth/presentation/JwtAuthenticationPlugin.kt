package ru.haykor.sudv.server.auth.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond
import ru.haykor.sudv.server.auth.domain.IAuthService
import ru.haykor.sudv.server.auth.domain.JwtService
import ru.haykor.sudv.server.core.http.PluginInstaller
import ru.haykor.sudv.server.core.http.PluginOrder
import ru.haykor.sudv.server.core.security.JWT_AUTH_NAME
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.contract.ErrorResponse

@Inject
@ContributesIntoSet(AppScope::class)
class JwtAuthenticationPlugin(private val authService: IAuthService, private val jwtService: JwtService) : PluginInstaller {
    override val order = PluginOrder.AUTHENTICATION

    override fun Application.install() {
        install(Authentication) {
            jwt(JWT_AUTH_NAME) {
                verifier(jwtService.verifier)
                validate { credential ->
                    jwtService.userId(credential.payload.subject)?.let { authService.authenticate(it) }
                }
                challenge { _, _ ->
                    call.respond(
                        HttpStatusCode.fromValue(ErrorCode.UNAUTHORIZED.httpStatus),
                        ErrorResponse(ErrorCode.UNAUTHORIZED, "Требуется авторизация"),
                    )
                }
            }
        }
    }
}
