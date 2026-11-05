package ru.haykor.sudv.server.records.presentation

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
import io.ktor.server.routing.put
import ru.haykor.sudv.server.core.http.RouteRegistrar
import ru.haykor.sudv.server.core.http.departmentIdOrNull
import ru.haykor.sudv.server.core.http.pathIdValue
import ru.haykor.sudv.server.core.security.authenticatedApi
import ru.haykor.sudv.server.core.security.requireRole
import ru.haykor.sudv.server.records.domain.IRecordService
import ru.haykor.sudv.shared.contract.ApiParams
import ru.haykor.sudv.shared.contract.ApiRoutes
import ru.haykor.sudv.shared.contract.EmployeeRequest
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.UserRole

@Inject
@ContributesIntoSet(AppScope::class)
class EmployeeRoutes(private val recordService: IRecordService) : RouteRegistrar {
    override fun Route.register() {
        authenticatedApi {
            /** Tag: Employees */
            get(ApiRoutes.EMPLOYEES) {
                val actor = call.requireRole(UserRole.ADMIN, UserRole.HR, UserRole.MANAGER)
                val params = call.request.queryParameters
                val query = params[ApiParams.QUERY]
                val departmentId = params.departmentIdOrNull(ApiParams.DEPARTMENT_ID)
                val includeArchived = params[ApiParams.INCLUDE_ARCHIVED]?.toBoolean() ?: false
                call.respond(recordService.findEmployees(actor, query, departmentId, includeArchived))
            }
            /** Tag: Employees */
            post(ApiRoutes.EMPLOYEES) {
                // ADMIN too: an account needs an employee, so the first HR account would otherwise be impossible to create.
                call.requireRole(UserRole.ADMIN, UserRole.HR)
                val request = call.receive<EmployeeRequest>()
                call.respond(HttpStatusCode.Created, recordService.createEmployee(request))
            }
            /** Tag: Employees */
            get(ApiRoutes.EMPLOYEE) {
                val actor = call.requireRole(UserRole.ADMIN, UserRole.HR, UserRole.MANAGER)
                val id = EmployeeId(call.pathIdValue())
                call.respond(recordService.getEmployee(actor, id))
            }
            /** Tag: Employees */
            put(ApiRoutes.EMPLOYEE) {
                call.requireRole(UserRole.HR)
                val id = EmployeeId(call.pathIdValue())
                val request = call.receive<EmployeeRequest>()
                call.respond(recordService.updateEmployee(id, request))
            }
            /** Tag: Employees */
            post(ApiRoutes.EMPLOYEE_ARCHIVE) {
                call.requireRole(UserRole.HR)
                val id = EmployeeId(call.pathIdValue())
                call.respond(recordService.archiveEmployee(id))
            }
        }
    }
}
