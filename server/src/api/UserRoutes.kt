package ru.haykor.sudv.api

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
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

@Inject
@ContributesIntoSet(AppScope::class)
class UserRoutes(private val authService: IAuthService) : RouteRegistrar {
    override fun Route.register() {
        authenticatedApi {
            /** Tag: Users */
            get(ApiRoutes.USERS) {
                call.requireRole(UserRole.ADMIN)
                call.respond(authService.listUsers())
            }
            /** Tag: Users */
            post(ApiRoutes.USERS) {
                call.requireRole(UserRole.ADMIN)
                val request = call.receive<CreateUserRequest>()
                call.respond(HttpStatusCode.Created, authService.createUser(request))
            }
            /** Tag: Users */
            post(ApiRoutes.USER_BLOCK) {
                val actor = call.requireRole(UserRole.ADMIN)
                val id = UserId(call.pathIdValue())
                call.respond(authService.setBlocked(actor, id, blocked = true))
            }
            /** Tag: Users */
            post(ApiRoutes.USER_UNBLOCK) {
                val actor = call.requireRole(UserRole.ADMIN)
                val id = UserId(call.pathIdValue())
                call.respond(authService.setBlocked(actor, id, blocked = false))
            }
        }
    }
}
