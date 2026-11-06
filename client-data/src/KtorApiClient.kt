package ru.haykor.sudv.client.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.decodeURLPart
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import ru.haykor.sudv.client.domain.ApiException
import ru.haykor.sudv.client.domain.FileContent
import ru.haykor.sudv.client.domain.IApiClient
import ru.haykor.sudv.client.domain.NetworkException
import ru.haykor.sudv.shared.contract.ApiParams
import ru.haykor.sudv.shared.contract.ApiRoutes
import ru.haykor.sudv.shared.contract.AttachmentDto
import ru.haykor.sudv.shared.contract.CreatePenaltyRequest
import ru.haykor.sudv.shared.contract.CreateReportRequest
import ru.haykor.sudv.shared.contract.CreateUserRequest
import ru.haykor.sudv.shared.contract.DepartmentDto
import ru.haykor.sudv.shared.contract.EmployeeCardDto
import ru.haykor.sudv.shared.contract.EmployeeDto
import ru.haykor.sudv.shared.contract.EmployeeRequest
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.contract.ErrorResponse
import ru.haykor.sudv.shared.contract.LoginRequest
import ru.haykor.sudv.shared.contract.LoginResponse
import ru.haykor.sudv.shared.contract.PenaltyDetailsDto
import ru.haykor.sudv.shared.contract.PenaltyDto
import ru.haykor.sudv.shared.contract.PenaltyFilter
import ru.haykor.sudv.shared.contract.PenaltyRemovalDto
import ru.haykor.sudv.shared.contract.ReportDto
import ru.haykor.sudv.shared.contract.SummaryReportDto
import ru.haykor.sudv.shared.contract.UserDto
import ru.haykor.sudv.shared.domain.AttachmentId
import ru.haykor.sudv.shared.domain.DepartmentId
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.PenaltyId
import ru.haykor.sudv.shared.domain.ReportFormat
import ru.haykor.sudv.shared.domain.ReportId
import ru.haykor.sudv.shared.domain.UserId

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
internal class KtorApiClient(
    private val httpClient: HttpClient,
    private val tokenStorage: ITokenStorage,
) : IApiClient {

    override val isSignedIn: Flow<Boolean> = tokenStorage.token.map { it != null }.distinctUntilChanged()

    override suspend fun login(request: LoginRequest): LoginResponse {
        val response: LoginResponse = post(ApiRoutes.LOGIN, request)
        tokenStorage.save(response.token)
        return response
    }

    override suspend fun logout() = tokenStorage.clear()

    override suspend fun me(): UserDto = get(ApiRoutes.ME)

    override suspend fun users(): List<UserDto> = get(ApiRoutes.USERS)

    override suspend fun createUser(request: CreateUserRequest): UserDto = post(ApiRoutes.USERS, request)

    override suspend fun blockUser(id: UserId): UserDto = send(HttpMethod.Post, ApiRoutes.userBlock(id)).body()

    override suspend fun unblockUser(id: UserId): UserDto = send(HttpMethod.Post, ApiRoutes.userUnblock(id)).body()

    override suspend fun departments(): List<DepartmentDto> = get(ApiRoutes.DEPARTMENTS)

    override suspend fun employees(query: String?, departmentId: DepartmentId?, includeArchived: Boolean): List<EmployeeDto> =
        get(ApiRoutes.EMPLOYEES) {
            parameter(ApiParams.QUERY, query)
            parameter(ApiParams.DEPARTMENT_ID, departmentId?.value)
            parameter(ApiParams.INCLUDE_ARCHIVED, includeArchived)
        }

    override suspend fun employee(id: EmployeeId): EmployeeDto = get(ApiRoutes.employee(id))

    override suspend fun createEmployee(request: EmployeeRequest): EmployeeDto = post(ApiRoutes.EMPLOYEES, request)

    override suspend fun updateEmployee(id: EmployeeId, request: EmployeeRequest): EmployeeDto =
        send(HttpMethod.Put, ApiRoutes.employee(id)) { jsonBody(request) }.body()

    override suspend fun archiveEmployee(id: EmployeeId): EmployeeDto = send(HttpMethod.Post, ApiRoutes.employeeArchive(id)).body()

    override suspend fun penalties(filter: PenaltyFilter): List<PenaltyDto> = get(ApiRoutes.PENALTIES) { penaltyFilter(filter) }

    override suspend fun penalty(id: PenaltyId): PenaltyDetailsDto = get(ApiRoutes.penalty(id))

    override suspend fun createPenalty(request: CreatePenaltyRequest): PenaltyDto = post(ApiRoutes.PENALTIES, request)

    override suspend fun removePenalty(id: PenaltyId, removal: PenaltyRemovalDto): PenaltyDto =
        post(ApiRoutes.penaltyRemoval(id), removal)

    override suspend fun uploadAttachment(penaltyId: PenaltyId, file: FileContent): AttachmentDto =
        send(HttpMethod.Post, ApiRoutes.penaltyAttachments(penaltyId)) {
            val parts = formData {
                append(
                    ApiParams.FILE,
                    file.bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, file.contentType)
                        // HTTP quoted-strings use backslash as an escape character, so '\' and '"' must be escaped.
                        val escapedFileName = file.fileName.replace("\\", "\\\\").replace("\"", "\\\"")
                        append(HttpHeaders.ContentDisposition, "filename=\"$escapedFileName\"")
                    },
                )
            }
            setBody(MultiPartFormDataContent(parts))
        }.body()

    override suspend fun downloadAttachment(id: AttachmentId): FileContent = download(ApiRoutes.attachment(id))

    override suspend fun deleteAttachment(id: AttachmentId) {
        send(HttpMethod.Delete, ApiRoutes.attachment(id))
    }

    override suspend fun employeeCard(id: EmployeeId): EmployeeCardDto = get(ApiRoutes.employeeCard(id))

    override suspend fun exportEmployeeCard(id: EmployeeId, format: ReportFormat): FileContent =
        download(ApiRoutes.employeeCardExport(id)) { parameter(ApiParams.FORMAT, format.name) }

    override suspend fun exportPenalties(filter: PenaltyFilter, format: ReportFormat): FileContent =
        download(ApiRoutes.PENALTIES_EXPORT) {
            penaltyFilter(filter)
            parameter(ApiParams.FORMAT, format.name)
        }

    override suspend fun summaryReport(
        periodFrom: LocalDate,
        periodTo: LocalDate,
        departmentIds: List<DepartmentId>,
    ): SummaryReportDto = get(ApiRoutes.REPORTS_SUMMARY) {
        parameter(ApiParams.PERIOD_FROM, periodFrom)
        parameter(ApiParams.PERIOD_TO, periodTo)
        departmentIds.forEach { parameter(ApiParams.DEPARTMENT_ID, it.value) }
    }

    override suspend fun reports(): List<ReportDto> = get(ApiRoutes.REPORTS)

    override suspend fun createReport(request: CreateReportRequest): ReportDto = post(ApiRoutes.REPORTS, request)

    override suspend fun downloadReport(id: ReportId): FileContent = download(ApiRoutes.reportFile(id))

    private suspend inline fun <reified T> get(path: String, noinline block: HttpRequestBuilder.() -> Unit = {}): T =
        send(HttpMethod.Get, path, block).body()

    private suspend inline fun <reified B : Any, reified T> post(path: String, body: B): T =
        send(HttpMethod.Post, path) { jsonBody(body) }.body()

    private suspend fun download(path: String, block: HttpRequestBuilder.() -> Unit = {}): FileContent {
        val response = send(HttpMethod.Get, path, block)
        return FileContent(
            fileName = response.headers[HttpHeaders.ContentDisposition]?.let(::fileNameFromDisposition) ?: "file",
            contentType = (response.contentType() ?: ContentType.Application.OctetStream).withoutParameters().toString(),
            bytes = response.bodyAsBytes(),
        )
    }

    /** Adds the stored token and turns failures into [NetworkException] and [ApiException]. */
    private suspend fun send(method: HttpMethod, path: String, block: HttpRequestBuilder.() -> Unit = {}): HttpResponse {
        val token = tokenStorage.token.first()
        val response = try {
            httpClient.request(path) {
                this.method = method
                token?.let { bearerAuth(it) }
                block()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Engines report an unreachable server with different exception types (IOException, JS errors).
            throw NetworkException(e)
        }
        if (!response.status.isSuccess()) throw response.toApiException()
        return response
    }

    private suspend fun HttpResponse.toApiException(): ApiException {
        val error = try {
            body<ErrorResponse>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // e.g. an HTML page from a proxy in front of the server
        }
        val exception = error?.let { ApiException(it.code, it.message) }
            ?: ApiException(ErrorCode.INTERNAL_ERROR, "Сервер вернул ошибку ${status.value}")
        if (exception.code == ErrorCode.UNAUTHORIZED) tokenStorage.clear()
        return exception
    }
}

private inline fun <reified T : Any> HttpRequestBuilder.jsonBody(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}

/** Ids are sent as their number: a value class' `toString()` would give `DepartmentId(value=1)`. */
private fun HttpRequestBuilder.penaltyFilter(filter: PenaltyFilter) {
    parameter(ApiParams.STATUS, filter.status?.name)
    parameter(ApiParams.EMPLOYEE_NAME, filter.employeeName)
    parameter(ApiParams.DEPARTMENT_ID, filter.departmentId?.value)
    parameter(ApiParams.ORDER_DATE_FROM, filter.orderDateFrom)
    parameter(ApiParams.ORDER_DATE_TO, filter.orderDateTo)
    parameter(ApiParams.SORT, filter.sort.name)
    parameter(ApiParams.DIRECTION, filter.direction.name)
}

/** The server sends `filename*=UTF-8''<percent-encoded>`; a plain `filename` is accepted as a fallback. */
internal fun fileNameFromDisposition(header: String): String? {
    val disposition = ContentDisposition.parse(header)
    disposition.parameter(ContentDisposition.Parameters.FileNameAsterisk)
        ?.substringAfter("''", missingDelimiterValue = "")
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it.decodeURLPart() }
    return disposition.parameter(ContentDisposition.Parameters.FileName)
}
