package ru.haykor.sudv.server.auth

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import ru.haykor.sudv.server.adminToken
import ru.haykor.sudv.server.api
import ru.haykor.sudv.server.apiTest
import ru.haykor.sudv.server.bearer
import ru.haykor.sudv.server.createEmployee
import ru.haykor.sudv.server.createUser
import ru.haykor.sudv.server.login
import ru.haykor.sudv.shared.contract.ApiRoutes
import ru.haykor.sudv.shared.contract.CreateUserRequest
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.contract.ErrorResponse
import ru.haykor.sudv.shared.contract.UserDto
import ru.haykor.sudv.shared.domain.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals

class UserManagementTest {
    @Test
    fun adminCreatesHrUser() = apiTest {
        val admin = adminToken()
        val employeeId = createEmployee()

        val response = api().post(ApiRoutes.USERS) {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody(CreateUserRequest("hr-user", "password123", UserRole.HR, employeeId))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val dto = response.body<UserDto>()
        assertEquals("hr-user", dto.login)
        assertEquals(UserRole.HR, dto.role)
        assertEquals(false, dto.isBlocked)
    }

    @Test
    fun duplicateLoginIsRejected() = apiTest {
        val admin = adminToken()
        val first = createEmployee()
        val second = createEmployee()

        api().post(ApiRoutes.USERS) {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody(CreateUserRequest("duplicate", "password123", UserRole.HR, first))
        }

        val response = api().post(ApiRoutes.USERS) {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody(CreateUserRequest("duplicate", "password456", UserRole.HR, second))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(ErrorCode.DUPLICATE_LOGIN, response.body<ErrorResponse>().code)
    }

    @Test
    fun employeeAlreadyHavingAnAccountIsRejected() = apiTest {
        val admin = adminToken()
        val employeeId = createEmployee()

        api().post(ApiRoutes.USERS) {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody(CreateUserRequest("first-account", "password123", UserRole.HR, employeeId))
        }

        val response = api().post(ApiRoutes.USERS) {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody(CreateUserRequest("second-account", "password123", UserRole.MANAGER, employeeId))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, response.body<ErrorResponse>().code)
    }

    @Test
    fun weakPasswordIsRejected() = apiTest {
        val admin = adminToken()
        val employeeId = createEmployee()

        val response = api().post(ApiRoutes.USERS) {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody(CreateUserRequest("weak-password-user", "short", UserRole.HR, employeeId))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, response.body<ErrorResponse>().code)
    }

    @Test
    fun blockingThenUnblockingTogglesTokenValidity() = apiTest {
        val admin = adminToken()
        val employeeId = createEmployee()

        val created = api().post(ApiRoutes.USERS) {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody(CreateUserRequest("to-block", "password123", UserRole.HR, employeeId))
        }.body<UserDto>()

        val userToken = login("to-block", "password123")
        assertEquals(HttpStatusCode.OK, client.get(ApiRoutes.DEPARTMENTS) { bearer(userToken) }.status)

        val blockResponse = api().post(ApiRoutes.userBlock(created.id)) { bearer(admin) }
        assertEquals(HttpStatusCode.OK, blockResponse.status)
        assertEquals(true, blockResponse.body<UserDto>().isBlocked)

        assertEquals(HttpStatusCode.Unauthorized, client.get(ApiRoutes.DEPARTMENTS) { bearer(userToken) }.status)

        val unblockResponse = api().post(ApiRoutes.userUnblock(created.id)) { bearer(admin) }
        assertEquals(HttpStatusCode.OK, unblockResponse.status)
        assertEquals(false, unblockResponse.body<UserDto>().isBlocked)

        val freshToken = login("to-block", "password123")
        assertEquals(HttpStatusCode.OK, client.get(ApiRoutes.DEPARTMENTS) { bearer(freshToken) }.status)
    }

    @Test
    fun blockingSelfIsRejected() = apiTest {
        val admin = adminToken()
        val adminDto = api().get(ApiRoutes.USERS) { bearer(admin) }.body<List<UserDto>>().single { it.login == "admin" }

        val response = api().post(ApiRoutes.userBlock(adminDto.id)) { bearer(admin) }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, response.body<ErrorResponse>().code)
    }

    @Test
    fun hrAndManagerCannotListUsers() = apiTest {
        val hrToken = createUser(UserRole.HR)
        val managerToken = createUser(UserRole.MANAGER)

        assertEquals(HttpStatusCode.Forbidden, client.get(ApiRoutes.USERS) { bearer(hrToken) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get(ApiRoutes.USERS) { bearer(managerToken) }.status)
    }
}
