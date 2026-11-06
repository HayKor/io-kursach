package ru.haykor.sudv.server.reports.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import ru.haykor.sudv.server.core.error.ApiException
import ru.haykor.sudv.server.core.http.RouteRegistrar
import ru.haykor.sudv.server.core.http.attachmentDisposition
import ru.haykor.sudv.server.core.http.departmentIds
import ru.haykor.sudv.server.core.http.enumOrNull
import ru.haykor.sudv.server.core.http.localDateOrNull
import ru.haykor.sudv.server.core.http.pathIdValue
import ru.haykor.sudv.server.core.security.authenticatedApi
import ru.haykor.sudv.server.core.security.requireRole
import ru.haykor.sudv.server.records.domain.StoredFile
import ru.haykor.sudv.server.records.presentation.toPenaltyFilter
import ru.haykor.sudv.server.reports.domain.IReportService
import ru.haykor.sudv.shared.contract.ApiParams
import ru.haykor.sudv.shared.contract.ApiRoutes
import ru.haykor.sudv.shared.contract.CreateReportRequest
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.ReportFormat
import ru.haykor.sudv.shared.domain.ReportId
import ru.haykor.sudv.shared.domain.UserRole

@Inject
@ContributesIntoSet(AppScope::class)
class ReportRoutes(private val reportService: IReportService) : RouteRegistrar {
    override fun Route.register() {
        authenticatedApi {
            /** Tag: Reports */
            get(ApiRoutes.EMPLOYEE_CARD) {
                val actor = call.requireRole(UserRole.HR, UserRole.MANAGER)
                val id = EmployeeId(call.pathIdValue())
                call.respond(reportService.employeeCard(actor, id))
            }

            /** Tag: Reports */
            get(ApiRoutes.EMPLOYEE_CARD_EXPORT) {
                val actor = call.requireRole(UserRole.HR, UserRole.MANAGER)
                val id = EmployeeId(call.pathIdValue())
                val format = call.request.queryParameters.reportFormatOrDefault()
                call.respondFile(reportService.exportEmployeeCard(actor, id, format))
            }

            /** Tag: Reports */
            get(ApiRoutes.PENALTIES_EXPORT) {
                val actor = call.requireRole(UserRole.HR, UserRole.MANAGER)
                val filter = call.request.queryParameters.toPenaltyFilter()
                val format = call.request.queryParameters.reportFormatOrDefault()
                call.respondFile(reportService.exportPenalties(actor, filter, format))
            }

            /** Tag: Reports */
            get(ApiRoutes.REPORTS_SUMMARY) {
                call.requireRole(UserRole.HR)
                val params = call.request.queryParameters
                val from = params.localDateOrNull(ApiParams.PERIOD_FROM)
                    ?: throw ApiException(ErrorCode.VALIDATION_FAILED, "Не указана дата начала периода")
                val to = params.localDateOrNull(ApiParams.PERIOD_TO)
                    ?: throw ApiException(ErrorCode.VALIDATION_FAILED, "Не указана дата окончания периода")
                call.respond(reportService.summary(from, to, params.departmentIds(ApiParams.DEPARTMENT_ID)))
            }

            /** Tag: Reports */
            get(ApiRoutes.REPORTS) {
                call.requireRole(UserRole.HR)
                call.respond(reportService.listReports())
            }

            /** Tag: Reports */
            post(ApiRoutes.REPORTS) {
                call.requireRole(UserRole.HR)
                val request = call.receive<CreateReportRequest>()
                call.respond(HttpStatusCode.Created, reportService.createReport(request))
            }

            /** Tag: Reports */
            get(ApiRoutes.REPORT_FILE) {
                call.requireRole(UserRole.HR)
                val id = ReportId(call.pathIdValue())
                call.respondFile(reportService.reportFile(id))
            }
        }
    }
}

private suspend fun ApplicationCall.respondFile(file: StoredFile) {
    response.header(HttpHeaders.ContentDisposition, attachmentDisposition(file.fileName))
    respondBytes(file.bytes, ContentType.parse(file.contentType))
}

/** A missing [ApiParams.FORMAT] defaults to XLSX; an unknown value is [ErrorCode.VALIDATION_FAILED]. */
private fun Parameters.reportFormatOrDefault(): ReportFormat =
    enumOrNull<ReportFormat>(ApiParams.FORMAT, "Некорректный формат экспорта") ?: ReportFormat.XLSX
