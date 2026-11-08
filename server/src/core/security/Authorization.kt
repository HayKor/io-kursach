package ru.haykor.sudv.server.core.security

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import ru.haykor.sudv.server.core.error.ApiException
import ru.haykor.sudv.shared.contract.ApiRoutes
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.domain.UserRole

const val JWT_AUTH_NAME = "jwt"

/** Wraps [block]'s routes in the `jwt` authentication provider; every API route except [ApiRoutes.LOGIN] needs this. */
fun Route.authenticatedApi(block: Route.() -> Unit) {
    authenticate(JWT_AUTH_NAME, build = block)
}

/** Fails with [ErrorCode.UNAUTHORIZED] when the call has no principal; any role passes. */
fun ApplicationCall.requireUser(): CurrentUser =
    principal<CurrentUser>() ?: throw ApiException(ErrorCode.UNAUTHORIZED, "Требуется авторизация")

/** Fails with [ErrorCode.FORBIDDEN] unless the current principal's role is one of [roles]. */
suspend fun ApplicationCall.requireRole(vararg roles: UserRole): CurrentUser {
    val user = requireUser()
    if (user.role !in roles) throw ApiException(ErrorCode.FORBIDDEN, "Недостаточно прав")
    return user
}
