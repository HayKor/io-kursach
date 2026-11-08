package ru.haykor.sudv.server.auth

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import ru.haykor.sudv.server.adminToken
import ru.haykor.sudv.server.api
import ru.haykor.sudv.server.apiTest
import ru.haykor.sudv.server.bearer
import ru.haykor.sudv.server.createUser
import ru.haykor.sudv.shared.contract.ApiRoutes
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.contract.ErrorResponse
import ru.haykor.sudv.shared.contract.UserDto
import ru.haykor.sudv.shared.domain.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals

class MeTest {
    @Test
    fun returnsTheTokenOwner() = apiTest {
        val token = createUser(UserRole.MANAGER)

        val response = api().get(ApiRoutes.ME) { bearer(token) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(UserRole.MANAGER, response.body<UserDto>().role)
    }

    @Test
    fun blockedUsersTokenIsRejected() = apiTest {
        val admin = adminToken()
        val token = createUser(UserRole.HR)
        val user = api().get(ApiRoutes.ME) { bearer(token) }.body<UserDto>()

        api().post(ApiRoutes.userBlock(user.id)) { bearer(admin) }

        val response = api().get(ApiRoutes.ME) { bearer(token) }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(ErrorCode.UNAUTHORIZED, response.body<ErrorResponse>().code)
    }
}
