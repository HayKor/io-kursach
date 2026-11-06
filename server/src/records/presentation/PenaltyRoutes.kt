package ru.haykor.sudv.server.records.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import ru.haykor.sudv.server.core.http.RouteRegistrar
import ru.haykor.sudv.server.core.http.departmentIdOrNull
import ru.haykor.sudv.server.core.http.enumOrNull
import ru.haykor.sudv.server.core.http.localDateOrNull
import ru.haykor.sudv.server.core.http.pathIdValue
import ru.haykor.sudv.server.core.security.authenticatedApi
import ru.haykor.sudv.server.core.security.requireRole
import ru.haykor.sudv.server.records.domain.IRecordService
import ru.haykor.sudv.shared.contract.ApiParams
import ru.haykor.sudv.shared.contract.ApiRoutes
import ru.haykor.sudv.shared.contract.CreatePenaltyRequest
import ru.haykor.sudv.shared.contract.PenaltyFilter
import ru.haykor.sudv.shared.contract.PenaltySortField
import ru.haykor.sudv.shared.contract.SortDirection
import ru.haykor.sudv.shared.domain.PenaltyId
import ru.haykor.sudv.shared.domain.PenaltyStatus
import ru.haykor.sudv.shared.domain.UserRole

@Inject
@ContributesIntoSet(AppScope::class)
class PenaltyRoutes(private val recordService: IRecordService) : RouteRegistrar {
    override fun Route.register() {
        authenticatedApi {
            /** Tag: Penalties */
            get(ApiRoutes.PENALTIES) {
                val actor = call.requireRole(UserRole.HR, UserRole.MANAGER)
                val filter = call.request.queryParameters.toPenaltyFilter()
                call.respond(recordService.findPenalties(actor, filter))
            }
            /** Tag: Penalties */
            post(ApiRoutes.PENALTIES) {
                call.requireRole(UserRole.HR)
                val request = call.receive<CreatePenaltyRequest>()
                call.respond(HttpStatusCode.Created, recordService.createPenalty(request))
            }
            /** Tag: Penalties */
            get(ApiRoutes.PENALTY) {
                val actor = call.requireRole(UserRole.HR, UserRole.MANAGER)
                val id = PenaltyId(call.pathIdValue())
                call.respond(recordService.getPenalty(actor, id))
            }
        }
    }
}

/** An unknown enum value or an unparsable date in any field is [ru.haykor.sudv.shared.contract.ErrorCode.VALIDATION_FAILED]. */
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
