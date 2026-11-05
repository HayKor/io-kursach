package ru.haykor.sudv.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import ru.haykor.sudv.auth.CurrentUser
import ru.haykor.sudv.auth.IAuthService
import ru.haykor.sudv.auth.JwtService
import ru.haykor.sudv.contract.ApiRoutes
import ru.haykor.sudv.contract.ErrorCode
import ru.haykor.sudv.contract.ErrorResponse
import ru.haykor.sudv.domain.UserRole

const val JWT_AUTH_NAME = "jwt"

fun Application.configureAuthentication(authService: IAuthService, jwtService: JwtService) {
    install(Authentication) {
        jwt(JWT_AUTH_NAME) {
            verifier(jwtService.verifier)
            validate { credential ->
                jwtService.userId(credential)?.let { authService.authenticate(it) }
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

/** Wraps [block]'s routes in the `jwt` authentication provider; every API route except [ApiRoutes.LOGIN] needs this. */
fun Route.authenticatedApi(block: Route.() -> Unit) {
    authenticate(JWT_AUTH_NAME, build = block)
}

/** Fails with [ErrorCode.FORBIDDEN] unless the current principal's role is one of [roles]. */
suspend fun ApplicationCall.requireRole(vararg roles: UserRole): CurrentUser {
    val user = principal<CurrentUser>() ?: throw ApiException(ErrorCode.UNAUTHORIZED, "Требуется авторизация")
    if (user.role !in roles) throw ApiException(ErrorCode.FORBIDDEN, "Недостаточно прав")
    return user
}

/** Parses the `{id}` path parameter; a missing or non-numeric value is a client error. */
fun ApplicationCall.pathIdValue(): Long =
    parameters[ApiRoutes.ID]?.toLongOrNull() ?: throw ApiException(ErrorCode.VALIDATION_FAILED, "Некорректный идентификатор")
