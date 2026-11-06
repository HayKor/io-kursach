package ru.haykor.sudv.server.auth

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import ru.haykor.sudv.server.adminToken
import ru.haykor.sudv.server.api
import ru.haykor.sudv.server.apiTest
import ru.haykor.sudv.server.bearer
import ru.haykor.sudv.server.createEmployee
import ru.haykor.sudv.shared.contract.ApiRoutes
import ru.haykor.sudv.shared.contract.CreateUserRequest
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.contract.ErrorResponse
import ru.haykor.sudv.shared.contract.LoginRequest
import ru.haykor.sudv.shared.contract.LoginResponse
import ru.haykor.sudv.shared.contract.UserDto
import ru.haykor.sudv.shared.domain.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private suspend fun ApplicationTestBuilder.loginRaw(login: String, password: String) =
    api().post(ApiRoutes.LOGIN) {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(login, password))
    }

class LoginTest {
    @Test
    fun correctPasswordReturnsTokenAndUser() = apiTest {
        val response = loginRaw("admin", "admin12345")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<LoginResponse>()
        assertEquals("admin", body.user.login)
        assertEquals(UserRole.ADMIN, body.user.role)
        assertTrue(body.token.isNotBlank())
    }

    @Test
    fun wrongPasswordAndUnknownLoginGiveTheSameError() = apiTest {
        val wrongPassword = loginRaw("admin", "wrong-password")
        val unknownLogin = loginRaw("no-such-user", "whatever1")

        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
        assertEquals(HttpStatusCode.Unauthorized, unknownLogin.status)

        val wrongBody = wrongPassword.body<ErrorResponse>()
        val unknownBody = unknownLogin.body<ErrorResponse>()
        assertEquals(ErrorCode.INVALID_CREDENTIALS, wrongBody.code)
        assertEquals(ErrorCode.INVALID_CREDENTIALS, unknownBody.code)
        assertEquals(wrongBody.message, unknownBody.message)
    }

    @Test
    fun blockedUserCannotLogIn() = apiTest {
        val admin = adminToken()
        val employeeId = createEmployee()
        val created = api().post(ApiRoutes.USERS) {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody(CreateUserRequest("to-be-blocked", "password123", UserRole.HR, employeeId))
        }.body<UserDto>()

        api().post(ApiRoutes.userBlock(created.id)) { bearer(admin) }

        val response = loginRaw("to-be-blocked", "password123")
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(ErrorCode.USER_BLOCKED, response.body<ErrorResponse>().code)
    }
}
