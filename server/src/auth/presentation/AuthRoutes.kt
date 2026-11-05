package ru.haykor.sudv.server.auth.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import ru.haykor.sudv.server.auth.domain.IAuthService
import ru.haykor.sudv.server.core.http.RouteRegistrar
import ru.haykor.sudv.shared.contract.ApiRoutes
import ru.haykor.sudv.shared.contract.LoginRequest

@Inject
@ContributesIntoSet(AppScope::class)
class AuthRoutes(private val authService: IAuthService) : RouteRegistrar {
    override fun Route.register() {
        /** Tag: Auth */
        post(ApiRoutes.LOGIN) {
            val request = call.receive<LoginRequest>()
            call.respond(authService.login(request))
        }
    }
}
