package ru.haykor.sudv.api

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import ru.haykor.sudv.contract.ApiRoutes
import ru.haykor.sudv.domain.UserRole
import ru.haykor.sudv.records.IRecordService

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
