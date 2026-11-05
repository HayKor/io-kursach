package ru.haykor.sudv.server.expiry.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import ru.haykor.sudv.server.core.http.RouteRegistrar
import ru.haykor.sudv.server.core.http.pathIdValue
import ru.haykor.sudv.server.core.security.authenticatedApi
import ru.haykor.sudv.server.core.security.requireRole
import ru.haykor.sudv.server.expiry.domain.IExpiryService
import ru.haykor.sudv.shared.contract.ApiRoutes
import ru.haykor.sudv.shared.contract.PenaltyRemovalDto
import ru.haykor.sudv.shared.domain.PenaltyId
import ru.haykor.sudv.shared.domain.UserRole

@Inject
@ContributesIntoSet(AppScope::class)
class PenaltyRemovalRoutes(private val expiryService: IExpiryService) : RouteRegistrar {
    override fun Route.register() {
        authenticatedApi {
            /** Tag: Penalties */
            post(ApiRoutes.PENALTY_REMOVAL) {
                call.requireRole(UserRole.HR)
                val id = PenaltyId(call.pathIdValue())
                val request = call.receive<PenaltyRemovalDto>()
                call.respond(expiryService.removeEarly(id, request))
            }
        }
    }
}
