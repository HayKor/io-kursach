package ru.haykor.sudv.api

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import ru.haykor.sudv.auth.IAuthService
import ru.haykor.sudv.contract.ApiRoutes
import ru.haykor.sudv.contract.LoginRequest

fun Route.configureAuthRoutes(authService: IAuthService) {
    post(ApiRoutes.LOGIN) {
        val request = call.receive<LoginRequest>()
        call.respond(authService.login(request))
    }
}
