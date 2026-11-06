package ru.haykor.sudv.client.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import ru.haykor.sudv.client.domain.ApiException
import ru.haykor.sudv.client.domain.NetworkException
import ru.haykor.sudv.shared.contract.ApiParams
import ru.haykor.sudv.shared.contract.ApiRoutes
import ru.haykor.sudv.shared.contract.DepartmentDto
import ru.haykor.sudv.shared.contract.EmployeeShortDto
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.contract.ErrorResponse
import ru.haykor.sudv.shared.contract.LoginRequest
import ru.haykor.sudv.shared.contract.LoginResponse
import ru.haykor.sudv.shared.contract.PenaltyFilter
import ru.haykor.sudv.shared.contract.UserDto
import ru.haykor.sudv.shared.domain.AttachmentId
import ru.haykor.sudv.shared.domain.DepartmentId
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.PenaltyStatus
import ru.haykor.sudv.shared.domain.UserId
import ru.haykor.sudv.shared.domain.UserRole
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class InMemoryTokenStorage(initial: String? = null) : ITokenStorage {
    val state = MutableStateFlow(initial)
    override val token: Flow<String?> = state

    override suspend fun save(token: String) {
        state.value = token
    }

    override suspend fun clear() {
        state.value = null
    }
}

private val USER = UserDto(
    id = UserId(1),
    login = "hr",
    role = UserRole.HR,
    isBlocked = false,
    employee = EmployeeShortDto(EmployeeId(1), "EMP-1", "Иванов", "Иван", null, DepartmentDto(DepartmentId(1), "ИТ-отдел")),
)

private fun apiClient(tokens: ITokenStorage, handler: MockRequestHandler) =
    KtorApiClient(createHttpClient(MockEngine(handler), ApiConfig("http://sudv.test")), tokens)

private fun MockRequestHandleScope.respondJson(json: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(json, status, headersOf(HttpHeaders.ContentType, "application/json"))

private fun MockRequestHandleScope.respondError(code: ErrorCode, message: String) =
    respondJson(Json.encodeToString(ErrorResponse(code, message)), HttpStatusCode.fromValue(code.httpStatus))

class KtorApiClientTest {
    @Test
    fun loginStoresTokenAndAuthorizesLaterCalls() = runTest {
        val tokens = InMemoryTokenStorage()
        val api = apiClient(tokens) { request ->
            when (request.url.encodedPath) {
                ApiRoutes.LOGIN -> respondJson(Json.encodeToString(LoginResponse("token-1", USER)))
                else -> {
                    assertEquals("Bearer token-1", request.headers[HttpHeaders.Authorization])
                    respondJson(Json.encodeToString(USER))
                }
            }
        }

        api.login(LoginRequest("hr", "password123"))

        assertEquals("token-1", tokens.state.value)
        assertTrue(api.isSignedIn.first())
        assertEquals(USER, api.me())
    }

    @Test
    fun logoutSignsOut() = runTest {
        val tokens = InMemoryTokenStorage("token-1")
        val api = apiClient(tokens) { error("no requests expected") }

        api.logout()

        assertFalse(api.isSignedIn.first())
    }

    @Test
    fun errorResponseBecomesApiException() = runTest {
        val tokens = InMemoryTokenStorage("token-1")
        val api = apiClient(tokens) { respondError(ErrorCode.FORBIDDEN, "Недостаточно прав") }

        val error = assertFailsWith<ApiException> { api.users() }

        assertEquals(ErrorCode.FORBIDDEN, error.code)
        assertEquals("Недостаточно прав", error.message)
        assertEquals("token-1", tokens.state.value)
    }

    @Test
    fun rejectedTokenIsCleared() = runTest {
        val tokens = InMemoryTokenStorage("expired")
        val api = apiClient(tokens) { respondError(ErrorCode.UNAUTHORIZED, "Требуется авторизация") }

        assertFailsWith<ApiException> { api.me() }

        assertNull(tokens.state.value)
    }

    @Test
    fun wrongPasswordKeepsSignedOut() = runTest {
        val tokens = InMemoryTokenStorage()
        val api = apiClient(tokens) { respondError(ErrorCode.INVALID_CREDENTIALS, "Неверный логин или пароль") }

        val error = assertFailsWith<ApiException> { api.login(LoginRequest("hr", "wrong")) }

        assertEquals(ErrorCode.INVALID_CREDENTIALS, error.code)
        assertNull(tokens.state.value)
    }

    @Test
    fun nonJsonErrorBecomesInternalError() = runTest {
        val api = apiClient(InMemoryTokenStorage()) {
            respond("<html>Bad Gateway</html>", HttpStatusCode.BadGateway, headersOf(HttpHeaders.ContentType, "text/html"))
        }

        val error = assertFailsWith<ApiException> { api.departments() }

        assertEquals(ErrorCode.INTERNAL_ERROR, error.code)
    }

    @Test
    fun unreachableServerBecomesNetworkException() = runTest {
        val api = apiClient(InMemoryTokenStorage()) { throw IOException("Connection refused") }

        assertFailsWith<NetworkException> { api.departments() }
    }

    @Test
    fun failedFetchOnWasmBecomesNetworkException() = runTest {
        // What Ktor's Js engine throws on Wasm when fetch is rejected, e.g. by CORS: an Error, not an Exception.
        val api = apiClient(InMemoryTokenStorage()) { throw Error("Fail to fetch") }

        assertFailsWith<NetworkException> { api.login(LoginRequest("admin", "admin12345")) }
    }

    @Test
    fun penaltyFilterIsSentAsQueryParameters() = runTest {
        val api = apiClient(InMemoryTokenStorage()) { request ->
            val params = request.url.parameters
            assertEquals("ACTIVE", params[ApiParams.STATUS])
            assertEquals("3", params[ApiParams.DEPARTMENT_ID])
            assertEquals("2026-01-01", params[ApiParams.ORDER_DATE_FROM])
            assertEquals("ORDER_DATE", params[ApiParams.SORT])
            assertEquals("DESC", params[ApiParams.DIRECTION])
            assertNull(params[ApiParams.EMPLOYEE_NAME])
            respondJson("[]")
        }

        api.penalties(
            PenaltyFilter(
                status = PenaltyStatus.ACTIVE,
                departmentId = DepartmentId(3),
                orderDateFrom = LocalDate(2026, 1, 1),
            ),
        )
    }

    @Test
    fun downloadReadsNonAsciiFileName() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val api = apiClient(InMemoryTokenStorage()) {
            respond(
                bytes,
                HttpStatusCode.OK,
                headersOf(
                    HttpHeaders.ContentType to listOf("application/pdf"),
                    // "Приказ.pdf", encoded the way the server's attachmentDisposition does it.
                    HttpHeaders.ContentDisposition to listOf("attachment; filename*=UTF-8''%D0%9F%D1%80%D0%B8%D0%BA%D0%B0%D0%B7.pdf"),
                ),
            )
        }

        val file = api.downloadAttachment(AttachmentId(1))

        assertEquals("Приказ.pdf", file.fileName)
        assertEquals("application/pdf", file.contentType)
        assertContentEquals(bytes, file.bytes)
    }
}
