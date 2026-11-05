package ru.haykor.sudv.api

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import ru.haykor.sudv.contract.ApiParams
import ru.haykor.sudv.contract.ApiRoutes
import ru.haykor.sudv.contract.CreatePenaltyRequest
import ru.haykor.sudv.contract.PenaltyFilter
import ru.haykor.sudv.contract.PenaltySortField
import ru.haykor.sudv.contract.SortDirection
import ru.haykor.sudv.domain.PenaltyId
import ru.haykor.sudv.domain.PenaltyStatus
import ru.haykor.sudv.domain.UserRole
import ru.haykor.sudv.records.IRecordService

fun Route.configurePenaltyRoutes(recordService: IRecordService) {
    get(ApiRoutes.PENALTIES) {
        val actor = call.requireRole(UserRole.HR, UserRole.MANAGER)
        val filter = call.request.queryParameters.toPenaltyFilter()
        call.respond(recordService.findPenalties(actor, filter))
    }
    post(ApiRoutes.PENALTIES) {
        call.requireRole(UserRole.HR)
        val request = call.receive<CreatePenaltyRequest>()
        call.respond(HttpStatusCode.Created, recordService.createPenalty(request))
    }
    get(ApiRoutes.PENALTY) {
        val actor = call.requireRole(UserRole.HR, UserRole.MANAGER)
        val id = PenaltyId(call.pathIdValue())
        call.respond(recordService.getPenalty(actor, id))
    }
}

/** An unknown enum value or an unparsable date in any field is [ru.haykor.sudv.contract.ErrorCode.VALIDATION_FAILED]. */
fun Parameters.toPenaltyFilter(): PenaltyFilter {
    val defaults = PenaltyFilter()
    return PenaltyFilter(
        status = enumOrNull<PenaltyStatus>(ApiParams.STATUS, "Некорректный статус взыскания"),
        employeeName = this[ApiParams.EMPLOYEE_NAME],
        departmentId = departmentIdOrNull(ApiParams.DEPARTMENT_ID),
        orderDateFrom = localDateOrNull(ApiParams.ORDER_DATE_FROM),
        orderDateTo = localDateOrNull(ApiParams.ORDER_DATE_TO),
        sort = enumOrNull<PenaltySortField>(ApiParams.SORT, "Некорректное поле сортировки") ?: defaults.sort,
        direction = enumOrNull<SortDirection>(ApiParams.DIRECTION, "Некорректное направление сортировки") ?: defaults.direction,
    )
}
