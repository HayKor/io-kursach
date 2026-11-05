package ru.haykor.sudv

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.haykor.sudv.api.authenticatedApi
import ru.haykor.sudv.api.configureAttachmentRoutes
import ru.haykor.sudv.api.configureAuthRoutes
import ru.haykor.sudv.api.configureDepartmentRoutes
import ru.haykor.sudv.api.configureEmployeeRoutes
import ru.haykor.sudv.api.configurePenaltyRoutes
import ru.haykor.sudv.api.configureReportRoutes
import ru.haykor.sudv.api.configureUserRoutes
import ru.haykor.sudv.auth.IAuthService
import ru.haykor.sudv.expiry.IExpiryService
import ru.haykor.sudv.records.IRecordService
import ru.haykor.sudv.reports.IReportService

fun Application.configureRouting(
    authService: IAuthService,
    recordService: IRecordService,
    expiryService: IExpiryService,
    reportService: IReportService,
    maxAttachmentBytes: Long,
) {
    routing {
        get("/health") {
            call.respondText("OK")
        }

        configureAuthRoutes(authService)

        authenticatedApi {
            configureUserRoutes(authService)
            configureDepartmentRoutes(recordService)
            configureEmployeeRoutes(recordService)
            configurePenaltyRoutes(recordService, expiryService)
            configureAttachmentRoutes(recordService, maxAttachmentBytes)
            configureReportRoutes(reportService)
        }
    }
}
