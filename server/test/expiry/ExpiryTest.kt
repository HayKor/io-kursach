package ru.haykor.sudv.server.expiry

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import ru.haykor.sudv.server.adminToken
import ru.haykor.sudv.server.api
import ru.haykor.sudv.server.apiTest
import ru.haykor.sudv.server.appGraph
import ru.haykor.sudv.server.bearer
import ru.haykor.sudv.server.createEmployee
import ru.haykor.sudv.server.createPenalty
import ru.haykor.sudv.server.createUser
import ru.haykor.sudv.shared.contract.ApiRoutes
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.contract.ErrorResponse
import ru.haykor.sudv.shared.contract.PenaltyDetailsDto
import ru.haykor.sudv.shared.contract.PenaltyDto
import ru.haykor.sudv.shared.contract.PenaltyRemovalDto
import ru.haykor.sudv.shared.domain.PenaltyId
import ru.haykor.sudv.shared.domain.PenaltyRules
import ru.haykor.sudv.shared.domain.PenaltyStatus
import ru.haykor.sudv.shared.domain.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

// Old enough that order date + 1 year is always in the past, regardless of when the suite runs.
private val FAR_PAST_ORDER_DATE = LocalDate(2024, 1, 10)

private suspend fun ApplicationTestBuilder.postRemoval(token: String, penaltyId: PenaltyId, removal: PenaltyRemovalDto): HttpResponse =
    api().post(ApiRoutes.penaltyRemoval(penaltyId)) {
        bearer(token)
        contentType(ContentType.Application.Json)
        setBody(removal)
    }

private suspend fun ApplicationTestBuilder.penaltyStatus(token: String, penaltyId: PenaltyId): PenaltyStatus =
    api().get(ApiRoutes.penalty(penaltyId)) { bearer(token) }.body<PenaltyDetailsDto>().penalty.status

class ExpiryTest {
    @Test
    fun expireDueLeavesPenaltyActiveTheDayBeforeExpiry() = apiTest {
        val hr = createUser(UserRole.HR)
        val employeeId = createEmployee()
        val orderDate = FAR_PAST_ORDER_DATE
        val penalty = createPenalty(employeeId, orderDate = orderDate, violationDate = orderDate.minus(7, DateTimeUnit.DAY), hrToken = hr)
        val expiry = PenaltyRules.expiryDate(orderDate)

        val expiredCount = appGraph().expiryService.expireDue(expiry.minus(1, DateTimeUnit.DAY))
        assertEquals(0, expiredCount)
        assertEquals(PenaltyStatus.ACTIVE, penaltyStatus(hr, penalty.id))
    }

    @Test
    fun expireDueExpiresOnDueDateAndIsIdempotent() = apiTest {
        val hr = createUser(UserRole.HR)
        val employeeId = createEmployee()
        val orderDate = FAR_PAST_ORDER_DATE
        val penalty = createPenalty(employeeId, orderDate = orderDate, violationDate = orderDate.minus(7, DateTimeUnit.DAY), hrToken = hr)
        val expiry = PenaltyRules.expiryDate(orderDate)

        val expiredCount = appGraph().expiryService.expireDue(expiry)
        assertEquals(1, expiredCount)

        val details = api().get(ApiRoutes.penalty(penalty.id)) { bearer(hr) }.body<PenaltyDetailsDto>()
        assertEquals(PenaltyStatus.EXPIRED, details.penalty.status)
        assertEquals(listOf(PenaltyStatus.ACTIVE, PenaltyStatus.EXPIRED), details.events.map { it.status })

        val secondRun = appGraph().expiryService.expireDue(expiry)
        assertEquals(0, secondRun)
        val detailsAfterSecondRun = api().get(ApiRoutes.penalty(penalty.id)) { bearer(hr) }.body<PenaltyDetailsDto>()
        assertEquals(2, detailsAfterSecondRun.events.size)
    }

    @Test
    fun expireDueDoesNotTouchRemovedPenalty() = apiTest {
        val hr = createUser(UserRole.HR)
        val employeeId = createEmployee()
        val orderDate = FAR_PAST_ORDER_DATE
        val penalty = createPenalty(employeeId, orderDate = orderDate, violationDate = orderDate.minus(7, DateTimeUnit.DAY), hrToken = hr)
        postRemoval(hr, penalty.id, PenaltyRemovalDto("REM-1", orderDate.plus(1, DateTimeUnit.DAY)))

        val farFuture = PenaltyRules.expiryDate(orderDate).plus(10, DateTimeUnit.YEAR)
        val expiredCount = appGraph().expiryService.expireDue(farFuture)
        assertEquals(0, expiredCount)
        assertEquals(PenaltyStatus.REMOVED, penaltyStatus(hr, penalty.id))
    }

    @Test
    fun earlyRemovalSucceedsAndRejectsRepeats() = apiTest {
        val hr = createUser(UserRole.HR)
        val employeeId = createEmployee()
        val orderDate = FAR_PAST_ORDER_DATE
        val penalty = createPenalty(employeeId, orderDate = orderDate, violationDate = orderDate.minus(7, DateTimeUnit.DAY), hrToken = hr)
        val removal = PenaltyRemovalDto("REM-42", orderDate.plus(3, DateTimeUnit.DAY))

        val response = postRemoval(hr, penalty.id, removal)
        assertEquals(HttpStatusCode.OK, response.status)
        val removed = response.body<PenaltyDto>()
        assertEquals(PenaltyStatus.REMOVED, removed.status)
        assertEquals(removal, removed.removal)

        val details = api().get(ApiRoutes.penalty(penalty.id)) { bearer(hr) }.body<PenaltyDetailsDto>()
        assertEquals(listOf(PenaltyStatus.ACTIVE, PenaltyStatus.REMOVED), details.events.map { it.status })

        val secondAttempt = postRemoval(hr, penalty.id, removal)
        assertEquals(HttpStatusCode.Conflict, secondAttempt.status)
        assertEquals(ErrorCode.PENALTY_NOT_ACTIVE, secondAttempt.body<ErrorResponse>().code)
    }

    @Test
    fun earlyRemovalRejectsExpiredPenalty() = apiTest {
        val hr = createUser(UserRole.HR)
        val employeeId = createEmployee()
        val orderDate = FAR_PAST_ORDER_DATE
        val penalty = createPenalty(employeeId, orderDate = orderDate, violationDate = orderDate.minus(7, DateTimeUnit.DAY), hrToken = hr)
        appGraph().expiryService.expireDue(PenaltyRules.expiryDate(orderDate))

        val response = postRemoval(hr, penalty.id, PenaltyRemovalDto("REM-1", orderDate.plus(1, DateTimeUnit.DAY)))
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(ErrorCode.PENALTY_NOT_ACTIVE, response.body<ErrorResponse>().code)
    }

    @Test
    fun earlyRemovalBeforeOrderDateIsRejected() = apiTest {
        val hr = createUser(UserRole.HR)
        val employeeId = createEmployee()
        val orderDate = FAR_PAST_ORDER_DATE
        val penalty = createPenalty(employeeId, orderDate = orderDate, violationDate = orderDate.minus(7, DateTimeUnit.DAY), hrToken = hr)

        val response = postRemoval(hr, penalty.id, PenaltyRemovalDto("REM-1", orderDate.minus(1, DateTimeUnit.DAY)))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, response.body<ErrorResponse>().code)
        assertEquals(PenaltyStatus.ACTIVE, penaltyStatus(hr, penalty.id))
    }

    @Test
    fun earlyRemovalOfUnknownPenaltyIsNotFound() = apiTest {
        val hr = createUser(UserRole.HR)
        val response = postRemoval(hr, PenaltyId(999_999_999), PenaltyRemovalDto("REM-1", FAR_PAST_ORDER_DATE))
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ErrorCode.NOT_FOUND, response.body<ErrorResponse>().code)
    }

    @Test
    fun earlyRemovalIsForbiddenForManagerAndAdmin() = apiTest {
        val hr = createUser(UserRole.HR)
        val employeeId = createEmployee()
        val orderDate = FAR_PAST_ORDER_DATE
        val penalty = createPenalty(employeeId, orderDate = orderDate, violationDate = orderDate.minus(7, DateTimeUnit.DAY), hrToken = hr)
        val removal = PenaltyRemovalDto("REM-1", orderDate.plus(1, DateTimeUnit.DAY))

        val managerToken = createUser(UserRole.MANAGER)
        assertEquals(HttpStatusCode.Forbidden, postRemoval(managerToken, penalty.id, removal).status)

        val admin = adminToken()
        assertEquals(HttpStatusCode.Forbidden, postRemoval(admin, penalty.id, removal).status)
    }

    @Test
    fun changeStatusRaceGuardLosesAfterRemoval() = apiTest {
        val hr = createUser(UserRole.HR)
        val employeeId = createEmployee()
        val orderDate = FAR_PAST_ORDER_DATE
        val penalty = createPenalty(employeeId, orderDate = orderDate, violationDate = orderDate.minus(7, DateTimeUnit.DAY), hrToken = hr)
        postRemoval(hr, penalty.id, PenaltyRemovalDto("REM-1", orderDate.plus(1, DateTimeUnit.DAY)))

        val changed = appGraph().penaltyRepository.changeStatus(penalty.id, PenaltyStatus.ACTIVE, PenaltyStatus.EXPIRED)
        assertFalse(changed)
        assertEquals(PenaltyStatus.REMOVED, penaltyStatus(hr, penalty.id))
    }

    @Test
    fun schedulerExpiresOverdueActivePenaltyShortlyAfterStart() {
        val orderDate = FAR_PAST_ORDER_DATE
        var createdId: PenaltyId? = null

        // Scheduler disabled (the apiTest default) while the overdue penalty is set up through the API.
        apiTest {
            val hr = createUser(UserRole.HR)
            val employeeId = createEmployee()
            val penalty = createPenalty(employeeId, orderDate = orderDate, violationDate = orderDate.minus(7, DateTimeUnit.DAY), hrToken = hr)
            createdId = penalty.id
        }
        val penaltyId = createdId ?: error("Penalty was not created")

        apiTest(wipe = false, config = mapOf("expiry.schedulerEnabled" to "true")) {
            val hr = createUser(UserRole.HR)
            withTimeout(5.seconds) {
                var status = PenaltyStatus.ACTIVE
                while (status != PenaltyStatus.EXPIRED) {
                    delay(100)
                    status = penaltyStatus(hr, penaltyId)
                }
            }
        }
    }
}
