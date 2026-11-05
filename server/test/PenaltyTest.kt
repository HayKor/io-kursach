package ru.haykor.sudv

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import ru.haykor.sudv.contract.ApiParams
import ru.haykor.sudv.contract.ApiRoutes
import ru.haykor.sudv.contract.CreatePenaltyRequest
import ru.haykor.sudv.contract.ErrorCode
import ru.haykor.sudv.contract.ErrorResponse
import ru.haykor.sudv.contract.PenaltyDetailsDto
import ru.haykor.sudv.contract.PenaltyDto
import ru.haykor.sudv.contract.PenaltySortField
import ru.haykor.sudv.contract.SortDirection
import ru.haykor.sudv.domain.EmployeeId
import ru.haykor.sudv.domain.PenaltyStatus
import ru.haykor.sudv.domain.PenaltyType
import ru.haykor.sudv.domain.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

private suspend fun ApplicationTestBuilder.getPenalties(token: String, params: Map<String, String> = emptyMap()): List<PenaltyDto> =
    api().get(ApiRoutes.PENALTIES) {
        bearer(token)
        params.forEach { (name, value) -> parameter(name, value) }
    }.body()

private suspend fun ApplicationTestBuilder.postPenalty(token: String, employeeId: EmployeeId, orderDate: LocalDate): HttpResponse =
    api().post(ApiRoutes.PENALTIES) {
        bearer(token)
        contentType(ContentType.Application.Json)
        setBody(CreatePenaltyRequest(employeeId, PenaltyType.REMARK, orderDate, "Нарушение", "ORD-X", orderDate))
    }

class PenaltyTest {
    @Test
    fun creatingPenaltySetsExpiryDateAndActiveStatusWithOneEvent() = apiTest {
        val hr = createUser(UserRole.HR)
        val employeeId = createEmployee()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val orderDate = today.minus(10, DateTimeUnit.DAY)
        val violationDate = orderDate.minus(2, DateTimeUnit.DAY)

        val created = createPenalty(employeeId, orderDate = orderDate, violationDate = violationDate, hrToken = hr)
        assertEquals(PenaltyStatus.ACTIVE, created.status)
        assertEquals(orderDate.plus(1, DateTimeUnit.YEAR), created.expiryDate)
        assertNull(created.removal)

        val details = api().get(ApiRoutes.penalty(created.id)) { bearer(hr) }.body<PenaltyDetailsDto>()
        assertEquals(created, details.penalty)
        assertEquals(1, details.events.size)
        assertEquals(PenaltyStatus.ACTIVE, details.events.single().status)
    }

    @Test
    fun archivedEmployeeRejectsPenaltyCreation() = apiTest {
        val hr = createUser(UserRole.HR)
        val employeeId = createEmployee()
        api().post(ApiRoutes.employeeArchive(employeeId)) { bearer(hr) }

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val response = postPenalty(hr, employeeId, today.minus(3, DateTimeUnit.DAY))

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, response.body<ErrorResponse>().code)
    }

    @Test
    fun futureOrderDateIsRejected() = apiTest {
        val hr = createUser(UserRole.HR)
        val employeeId = createEmployee()
        // Two days ahead: the server's "today" comes from app.timeZone, which may differ from the system zone.
        val future = Clock.System.todayIn(TimeZone.currentSystemDefault()).plus(2, DateTimeUnit.DAY)

        val response = postPenalty(hr, employeeId, future)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, response.body<ErrorResponse>().code)
    }

    @Test
    fun filtersWorkIndividuallyAndCombined() = apiTest {
        val hr = createUser(UserRole.HR)
        val itDept = departmentId("ИТ-отдел")
        val accountingDept = departmentId("Бухгалтерия")

        val employeeInIt1 = createEmployee(personnelNumber = "FLT-1", lastName = "Орлов", departmentName = "ИТ-отдел")
        val employeeInIt2 = createEmployee(personnelNumber = "FLT-2", lastName = "Соколов", departmentName = "ИТ-отдел")
        val employeeInAccounting = createEmployee(personnelNumber = "FLT-3", lastName = "Орлова", departmentName = "Бухгалтерия")

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val old = createPenalty(employeeInIt1, type = PenaltyType.REMARK, orderDate = today.minus(30, DateTimeUnit.DAY), hrToken = hr)
        val mid = createPenalty(employeeInIt2, type = PenaltyType.REPRIMAND, orderDate = today.minus(20, DateTimeUnit.DAY), hrToken = hr)
        val recent = createPenalty(employeeInAccounting, type = PenaltyType.REMARK, orderDate = today.minus(10, DateTimeUnit.DAY), hrToken = hr)
        val ids = setOf(old.id, mid.id, recent.id)

        val byDepartment = getPenalties(hr, mapOf(ApiParams.DEPARTMENT_ID to itDept.value.toString())).filter { it.id in ids }
        assertEquals(setOf(old.id, mid.id), byDepartment.map { it.id }.toSet())

        val byStatus = getPenalties(hr, mapOf(ApiParams.STATUS to PenaltyStatus.ACTIVE.name)).filter { it.id in ids }
        assertEquals(ids, byStatus.map { it.id }.toSet())

        val byName = getPenalties(hr, mapOf(ApiParams.EMPLOYEE_NAME to "орлов")).filter { it.id in ids }
        assertEquals(setOf(old.id, recent.id), byName.map { it.id }.toSet())

        val byDateRange = getPenalties(
            hr,
            mapOf(
                ApiParams.ORDER_DATE_FROM to today.minus(25, DateTimeUnit.DAY).toString(),
                ApiParams.ORDER_DATE_TO to today.minus(10, DateTimeUnit.DAY).toString(),
            ),
        ).filter { it.id in ids }
        assertEquals(setOf(mid.id, recent.id), byDateRange.map { it.id }.toSet())

        val combinedDepartmentAndStatus = getPenalties(
            hr,
            mapOf(ApiParams.DEPARTMENT_ID to itDept.value.toString(), ApiParams.STATUS to PenaltyStatus.ACTIVE.name),
        ).filter { it.id in ids }
        assertEquals(setOf(old.id, mid.id), combinedDepartmentAndStatus.map { it.id }.toSet())

        val combinedNameAndDepartment = getPenalties(
            hr,
            mapOf(ApiParams.EMPLOYEE_NAME to "орлов", ApiParams.DEPARTMENT_ID to accountingDept.value.toString()),
        ).filter { it.id in ids }
        assertEquals(setOf(recent.id), combinedNameAndDepartment.map { it.id }.toSet())
    }

    @Test
    fun sortsByEmployeeNameAscendingAndOrderDateDescending() = apiTest {
        val hr = createUser(UserRole.HR)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        val employeeV = createEmployee(personnelNumber = "SRT-1", lastName = "Васильев")
        val employeeB = createEmployee(personnelNumber = "SRT-2", lastName = "Борисов")
        val employeeA = createEmployee(personnelNumber = "SRT-3", lastName = "Аникин")

        val pV = createPenalty(employeeV, orderDate = today.minus(30, DateTimeUnit.DAY), hrToken = hr)
        val pB = createPenalty(employeeB, orderDate = today.minus(20, DateTimeUnit.DAY), hrToken = hr)
        val pA = createPenalty(employeeA, orderDate = today.minus(10, DateTimeUnit.DAY), hrToken = hr)
        val ids = setOf(pV.id, pB.id, pA.id)

        val byNameAsc = getPenalties(
            hr,
            mapOf(ApiParams.SORT to PenaltySortField.EMPLOYEE_NAME.name, ApiParams.DIRECTION to SortDirection.ASC.name),
        ).filter { it.id in ids }
        assertEquals(listOf(pA.id, pB.id, pV.id), byNameAsc.map { it.id })

        val byOrderDateDesc = getPenalties(
            hr,
            mapOf(ApiParams.SORT to PenaltySortField.ORDER_DATE.name, ApiParams.DIRECTION to SortDirection.DESC.name),
        ).filter { it.id in ids }
        assertEquals(listOf(pA.id, pB.id, pV.id), byOrderDateDesc.map { it.id })
    }

    @Test
    fun managerIsScopedToOwnDepartment() = apiTest {
        val hr = createUser(UserRole.HR)
        val itDept = departmentId("ИТ-отдел")
        val accountingDept = departmentId("Бухгалтерия")
        val managerToken = createUser(UserRole.MANAGER, departmentName = "ИТ-отдел")

        val ownEmployee = createEmployee(personnelNumber = "MGR-OWN", departmentName = "ИТ-отдел")
        val otherEmployee = createEmployee(personnelNumber = "MGR-OTHER", departmentName = "Бухгалтерия")
        val ownPenalty = createPenalty(ownEmployee, hrToken = hr)
        val otherPenalty = createPenalty(otherEmployee, hrToken = hr)

        val list = getPenalties(managerToken)
        assertTrue(list.all { it.employee.department.id == itDept })
        assertTrue(list.any { it.id == ownPenalty.id })
        assertTrue(list.none { it.id == otherPenalty.id })

        val foreignDepartmentQuery = api().get(ApiRoutes.PENALTIES) {
            bearer(managerToken)
            parameter(ApiParams.DEPARTMENT_ID, accountingDept.value.toString())
        }
        assertEquals(HttpStatusCode.Forbidden, foreignDepartmentQuery.status)

        val ownDepartmentQuery = api().get(ApiRoutes.PENALTIES) {
            bearer(managerToken)
            parameter(ApiParams.DEPARTMENT_ID, itDept.value.toString())
        }
        assertEquals(HttpStatusCode.OK, ownDepartmentQuery.status)

        assertEquals(HttpStatusCode.Forbidden, api().get(ApiRoutes.penalty(otherPenalty.id)) { bearer(managerToken) }.status)
        assertEquals(HttpStatusCode.Forbidden, api().get(ApiRoutes.employee(otherEmployee)) { bearer(managerToken) }.status)
        assertEquals(HttpStatusCode.OK, api().get(ApiRoutes.penalty(ownPenalty.id)) { bearer(managerToken) }.status)
        assertEquals(HttpStatusCode.OK, api().get(ApiRoutes.employee(ownEmployee)) { bearer(managerToken) }.status)

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val createAttempt = postPenalty(managerToken, ownEmployee, today.minus(3, DateTimeUnit.DAY))
        assertEquals(HttpStatusCode.Forbidden, createAttempt.status)
    }

    @Test
    fun adminCanListEmployeesButNotPenalties() = apiTest {
        val admin = adminToken()
        val employeeId = createEmployee()
        createPenalty(employeeId)

        assertEquals(HttpStatusCode.OK, api().get(ApiRoutes.EMPLOYEES) { bearer(admin) }.status)
        assertEquals(HttpStatusCode.Forbidden, api().get(ApiRoutes.PENALTIES) { bearer(admin) }.status)
    }
}
