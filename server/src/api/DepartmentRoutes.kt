package ru.haykor.sudv.api

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import ru.haykor.sudv.contract.ApiRoutes
import ru.haykor.sudv.db.IEmployeeRepository
import ru.haykor.sudv.domain.UserRole

fun Route.configureDepartmentRoutes(employeeRepository: IEmployeeRepository) {
    get(ApiRoutes.DEPARTMENTS) {
        call.requireRole(UserRole.ADMIN, UserRole.HR, UserRole.MANAGER)
        call.respond(employeeRepository.findAllDepartments())
    }
}
