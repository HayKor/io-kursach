package ru.haykor.sudv

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.haykor.sudv.api.authenticatedApi
import ru.haykor.sudv.api.configureAuthRoutes
import ru.haykor.sudv.api.configureDepartmentRoutes
import ru.haykor.sudv.api.configureUserRoutes
import ru.haykor.sudv.auth.IAuthService
import ru.haykor.sudv.db.IEmployeeRepository

fun Application.configureRouting(authService: IAuthService, employeeRepository: IEmployeeRepository) {
    routing {
        get("/health") {
            call.respondText("OK")
        }

        configureAuthRoutes(authService)

        authenticatedApi {
            configureUserRoutes(authService)
            configureDepartmentRoutes(employeeRepository)
        }
    }
}
