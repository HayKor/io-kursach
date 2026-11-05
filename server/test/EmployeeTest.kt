package ru.haykor.sudv

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import ru.haykor.sudv.contract.ApiParams
import ru.haykor.sudv.contract.ApiRoutes
import ru.haykor.sudv.contract.CreateUserRequest
import ru.haykor.sudv.contract.EmployeeDto
import ru.haykor.sudv.contract.EmployeeRequest
import ru.haykor.sudv.contract.ErrorCode
import ru.haykor.sudv.contract.ErrorResponse
import ru.haykor.sudv.contract.LoginRequest
import ru.haykor.sudv.domain.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmployeeTest {
    @Test
    fun createUpdateAndArchiveLifecycle() = apiTest {
        val hr = createUser(UserRole.HR)
        val departmentId = departmentId("ИТ-отдел")

        val created = api().post(ApiRoutes.EMPLOYEES) {
            bearer(hr)
            contentType(ContentType.Application.Json)
            setBody(EmployeeRequest("LC-1", "Петров", "Пётр", "Петрович", "Инженер", departmentId))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val createdDto = created.body<EmployeeDto>()
        assertEquals("LC-1", createdDto.personnelNumber)
        assertFalse(createdDto.isArchived)

        val updated = api().put(ApiRoutes.employee(createdDto.id)) {
            bearer(hr)
            contentType(ContentType.Application.Json)
            setBody(EmployeeRequest("LC-1", "Петров", "Пётр", "Петрович", "Старший инженер", departmentId))
        }
        assertEquals(HttpStatusCode.OK, updated.status)
        assertEquals("Старший инженер", updated.body<EmployeeDto>().position)

        val archived = api().post(ApiRoutes.employeeArchive(createdDto.id)) { bearer(hr) }
        assertEquals(HttpStatusCode.OK, archived.status)
        assertTrue(archived.body<EmployeeDto>().isArchived)

        // Archiving an already archived employee is a no-op, not an error.
        val archivedAgain = api().post(ApiRoutes.employeeArchive(createdDto.id)) { bearer(hr) }
        assertEquals(HttpStatusCode.OK, archivedAgain.status)
        assertTrue(archivedAgain.body<EmployeeDto>().isArchived)
    }

    @Test
    fun archivedEmployeeIsHiddenByDefaultAndVisibleWithIncludeArchived() = apiTest {
        val hr = createUser(UserRole.HR)
        val employeeId = createEmployee(personnelNumber = "ARCH-1")
        api().post(ApiRoutes.employeeArchive(employeeId)) { bearer(hr) }

        val defaultList = api().get(ApiRoutes.EMPLOYEES) { bearer(hr) }.body<List<EmployeeDto>>()
        assertTrue(defaultList.none { it.id == employeeId })

        val fullList = api().get(ApiRoutes.EMPLOYEES) {
            bearer(hr)
            parameter(ApiParams.INCLUDE_ARCHIVED, "true")
        }.body<List<EmployeeDto>>()
        assertTrue(fullList.any { it.id == employeeId })
    }

    @Test
    fun searchMatchesPartOfLastNameAndPersonnelNumber() = apiTest {
        val hr = createUser(UserRole.HR)
        val employeeId = createEmployee(personnelNumber = "UNIQUE-777", lastName = "Сидоров")

        val byName = api().get(ApiRoutes.EMPLOYEES) {
            bearer(hr)
            parameter(ApiParams.QUERY, "идоров")
        }.body<List<EmployeeDto>>()
        assertTrue(byName.any { it.id == employeeId })

        val byNumber = api().get(ApiRoutes.EMPLOYEES) {
            bearer(hr)
            parameter(ApiParams.QUERY, "nique-777")
        }.body<List<EmployeeDto>>()
        assertTrue(byNumber.any { it.id == employeeId })
    }

    @Test
    fun duplicatePersonnelNumberIsRejectedOnCreateAndUpdate() = apiTest {
        val hr = createUser(UserRole.HR)
        val departmentId = departmentId("ИТ-отдел")
        createEmployee(personnelNumber = "DUP-1")
        val second = createEmployee(personnelNumber = "DUP-2")

        val createResponse = api().post(ApiRoutes.EMPLOYEES) {
            bearer(hr)
            contentType(ContentType.Application.Json)
            setBody(EmployeeRequest("DUP-1", "Фамилия", "Имя", null, "Должность", departmentId))
        }
        assertEquals(HttpStatusCode.Conflict, createResponse.status)
        assertEquals(ErrorCode.DUPLICATE_PERSONNEL_NUMBER, createResponse.body<ErrorResponse>().code)

        val updateResponse = api().put(ApiRoutes.employee(second)) {
            bearer(hr)
            contentType(ContentType.Application.Json)
            setBody(EmployeeRequest("DUP-1", "Фамилия", "Имя", null, "Должность", departmentId))
        }
        assertEquals(HttpStatusCode.Conflict, updateResponse.status)
        assertEquals(ErrorCode.DUPLICATE_PERSONNEL_NUMBER, updateResponse.body<ErrorResponse>().code)
    }

    @Test
    fun archivingEmployeeBlocksAccountLoginAndInvalidatesExistingToken() = apiTest {
        val admin = adminToken()
        val hr = createUser(UserRole.HR)
        val employeeId = createEmployee(personnelNumber = "TO-ARCHIVE")

        val loginName = "manager-to-archive"
        val password = "password123"
        api().post(ApiRoutes.USERS) {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody(CreateUserRequest(loginName, password, UserRole.MANAGER, employeeId))
        }
        val managerToken = login(loginName, password)
        assertEquals(HttpStatusCode.OK, client.get(ApiRoutes.DEPARTMENTS) { bearer(managerToken) }.status)

        val archiveResponse = api().post(ApiRoutes.employeeArchive(employeeId)) { bearer(hr) }
        assertEquals(HttpStatusCode.OK, archiveResponse.status)

        val loginAfterArchive = api().post(ApiRoutes.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(loginName, password))
        }
        assertEquals(HttpStatusCode.Forbidden, loginAfterArchive.status)
        assertEquals(ErrorCode.USER_BLOCKED, loginAfterArchive.body<ErrorResponse>().code)

        val requestWithOldToken = client.get(ApiRoutes.DEPARTMENTS) { bearer(managerToken) }
        assertEquals(HttpStatusCode.Unauthorized, requestWithOldToken.status)
    }
}
