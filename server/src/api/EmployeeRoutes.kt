package ru.haykor.sudv.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import ru.haykor.sudv.contract.ApiParams
import ru.haykor.sudv.contract.ApiRoutes
import ru.haykor.sudv.contract.EmployeeRequest
import ru.haykor.sudv.domain.EmployeeId
import ru.haykor.sudv.domain.UserRole
import ru.haykor.sudv.records.IRecordService

fun Route.configureEmployeeRoutes(recordService: IRecordService) {
    get(ApiRoutes.EMPLOYEES) {
        val actor = call.requireRole(UserRole.ADMIN, UserRole.HR, UserRole.MANAGER)
        val params = call.request.queryParameters
        val query = params[ApiParams.QUERY]
        val departmentId = params.departmentIdOrNull(ApiParams.DEPARTMENT_ID)
        val includeArchived = params[ApiParams.INCLUDE_ARCHIVED]?.toBoolean() ?: false
        call.respond(recordService.findEmployees(actor, query, departmentId, includeArchived))
    }
    post(ApiRoutes.EMPLOYEES) {
        call.requireRole(UserRole.HR)
        val request = call.receive<EmployeeRequest>()
        call.respond(HttpStatusCode.Created, recordService.createEmployee(request))
    }
    get(ApiRoutes.EMPLOYEE) {
        val actor = call.requireRole(UserRole.ADMIN, UserRole.HR, UserRole.MANAGER)
        val id = EmployeeId(call.pathIdValue())
        call.respond(recordService.getEmployee(actor, id))
    }
    put(ApiRoutes.EMPLOYEE) {
        call.requireRole(UserRole.HR)
        val id = EmployeeId(call.pathIdValue())
        val request = call.receive<EmployeeRequest>()
        call.respond(recordService.updateEmployee(id, request))
    }
    post(ApiRoutes.EMPLOYEE_ARCHIVE) {
        call.requireRole(UserRole.HR)
        val id = EmployeeId(call.pathIdValue())
        call.respond(recordService.archiveEmployee(id))
    }
}
