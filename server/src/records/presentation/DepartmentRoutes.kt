package ru.haykor.sudv.server.records.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import ru.haykor.sudv.server.core.http.RouteRegistrar
import ru.haykor.sudv.server.core.security.authenticatedApi
import ru.haykor.sudv.server.core.security.requireRole
import ru.haykor.sudv.server.records.domain.IRecordService
import ru.haykor.sudv.shared.contract.ApiRoutes
import ru.haykor.sudv.shared.domain.UserRole

@Inject
@ContributesIntoSet(AppScope::class)
class DepartmentRoutes(private val recordService: IRecordService) : RouteRegistrar {
    override fun Route.register() {
        authenticatedApi {
            /** Tag: Departments */
            get(ApiRoutes.DEPARTMENTS) {
                call.requireRole(UserRole.ADMIN, UserRole.HR, UserRole.MANAGER)
                call.respond(recordService.departments())
            }
        }
    }
}
