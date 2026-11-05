package ru.haykor.sudv.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import ru.haykor.sudv.auth.IAuthService
import ru.haykor.sudv.contract.ApiRoutes
import ru.haykor.sudv.contract.CreateUserRequest
import ru.haykor.sudv.domain.UserId
import ru.haykor.sudv.domain.UserRole

fun Route.configureUserRoutes(authService: IAuthService) {
    get(ApiRoutes.USERS) {
        call.requireRole(UserRole.ADMIN)
        call.respond(authService.listUsers())
    }
    post(ApiRoutes.USERS) {
        call.requireRole(UserRole.ADMIN)
        val request = call.receive<CreateUserRequest>()
        call.respond(HttpStatusCode.Created, authService.createUser(request))
    }
    post(ApiRoutes.USER_BLOCK) {
        val actor = call.requireRole(UserRole.ADMIN)
        val id = UserId(call.pathIdValue())
        call.respond(authService.setBlocked(actor, id, blocked = true))
    }
    post(ApiRoutes.USER_UNBLOCK) {
        val actor = call.requireRole(UserRole.ADMIN)
        val id = UserId(call.pathIdValue())
        call.respond(authService.setBlocked(actor, id, blocked = false))
    }
}
