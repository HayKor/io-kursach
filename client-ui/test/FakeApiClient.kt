package ru.haykor.sudv.client.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import ru.haykor.sudv.client.domain.FileContent
import ru.haykor.sudv.client.domain.IApiClient
import ru.haykor.sudv.shared.contract.AttachmentDto
import ru.haykor.sudv.shared.contract.CreatePenaltyRequest
import ru.haykor.sudv.shared.contract.CreateReportRequest
import ru.haykor.sudv.shared.contract.CreateUserRequest
import ru.haykor.sudv.shared.contract.DepartmentDto
import ru.haykor.sudv.shared.contract.EmployeeCardDto
import ru.haykor.sudv.shared.contract.EmployeeDto
import ru.haykor.sudv.shared.contract.EmployeeRequest
import ru.haykor.sudv.shared.contract.EmployeeShortDto
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
import ru.haykor.sudv.shared.domain.PenaltyRules
import ru.haykor.sudv.shared.domain.PenaltyStatus
import ru.haykor.sudv.shared.domain.PenaltyType
import ru.haykor.sudv.shared.domain.ReportFormat
import ru.haykor.sudv.shared.domain.ReportId
import ru.haykor.sudv.shared.domain.UserId
import ru.haykor.sudv.shared.domain.UserRole
import kotlin.time.Instant

val TEST_USER = UserDto(
    id = UserId(1),
    login = "hr",
    role = UserRole.HR,
    isBlocked = false,
    employee = EmployeeShortDto(EmployeeId(1), "EMP-1", "Иванов", "Иван", null, DepartmentDto(DepartmentId(1), "ИТ-отдел")),
)

val IT_DEPARTMENT = DepartmentDto(DepartmentId(1), "ИТ-отдел")
val HR_DEPARTMENT = DepartmentDto(DepartmentId(2), "Отдел кадров")

fun employee(id: Long, lastName: String = "Иванов", isArchived: Boolean = false) = EmployeeDto(
    id = EmployeeId(id),
    personnelNumber = "EMP-$id",
    lastName = lastName,
    firstName = "Иван",
    middleName = null,
    position = "Инженер",
    department = IT_DEPARTMENT,
    isArchived = isArchived,
)

fun account(id: Long, login: String, role: UserRole = UserRole.HR, employeeId: Long = id, isBlocked: Boolean = false) = UserDto(
    id = UserId(id),
    login = login,
    role = role,
    isBlocked = isBlocked,
    employee = EmployeeShortDto(EmployeeId(employeeId), "EMP-$employeeId", "Иванов", "Иван", null, IT_DEPARTMENT),
)

fun penalty(
    id: Long,
    status: PenaltyStatus = PenaltyStatus.ACTIVE,
    orderDate: LocalDate = LocalDate(2026, 3, 1),
    removal: PenaltyRemovalDto? = null,
) = PenaltyDto(
    id = PenaltyId(id),
    employee = EmployeeShortDto(EmployeeId(1), "EMP-1", "Иванов", "Иван", null, IT_DEPARTMENT),
    type = PenaltyType.REMARK,
    violationDate = orderDate,
    description = "Опоздание",
    orderNumber = "$id-к",
    orderDate = orderDate,
    expiryDate = PenaltyRules.expiryDate(orderDate),
    status = status,
    removal = removal,
)

val PDF_BYTES = "%PDF-1.4".encodeToByteArray()

fun attachment(id: Long, fileName: String = "Приказ.pdf") = AttachmentDto(
    id = AttachmentId(id),
    fileName = fileName,
    contentType = "application/pdf",
    sizeBytes = 2048,
    uploadedAt = Instant.parse("2026-11-07T10:00:00Z"),
)

/** Arguments of one [IApiClient.employees] call. */
data class EmployeesQuery(val query: String?, val departmentId: DepartmentId?, val includeArchived: Boolean)

/**
 * Mimics the real client's session handling: [login] stores a token, [logout] clears it.
 * Routes a test needs are answered by the `on…` handlers and recorded; any other route fails the test.
 */
class FakeApiClient(signedIn: Boolean) : IApiClient {
    override val isSignedIn = MutableStateFlow(signedIn)
    var onLogin: suspend (LoginRequest) -> UserDto = { TEST_USER }
    var onMe: suspend () -> UserDto = { TEST_USER }

    val employeesQueries = mutableListOf<EmployeesQuery>()
    var onEmployees: suspend (EmployeesQuery) -> List<EmployeeDto> = { listOf(employee(1), employee(2, "Петров")) }
    var departmentsCalls = 0
    var onDepartments: suspend () -> List<DepartmentDto> = { listOf(IT_DEPARTMENT, HR_DEPARTMENT) }
    val employeeCalls = mutableListOf<EmployeeId>()
    var onEmployee: suspend (EmployeeId) -> EmployeeDto = { employee(it.value) }
    val createdEmployees = mutableListOf<EmployeeRequest>()
    var onCreateEmployee: suspend (EmployeeRequest) -> EmployeeDto = { employee(99, it.lastName) }
    val updatedEmployees = mutableListOf<Pair<EmployeeId, EmployeeRequest>>()
    var onUpdateEmployee: suspend (EmployeeId, EmployeeRequest) -> EmployeeDto = { id, request -> employee(id.value, request.lastName) }
    val archivedEmployees = mutableListOf<EmployeeId>()

    var usersCalls = 0
    var onUsers: suspend () -> List<UserDto> = { listOf(account(1, "admin", UserRole.ADMIN), account(2, "hr1")) }
    val createdUsers = mutableListOf<CreateUserRequest>()
    var onCreateUser: suspend (CreateUserRequest) -> UserDto = { account(50, it.login, it.role, it.employeeId.value) }
    val blockCalls = mutableListOf<Pair<UserId, Boolean>>()
    var onSetBlocked: suspend (UserId, Boolean) -> UserDto = { id, blocked -> account(id.value, "user${id.value}", isBlocked = blocked) }

    val penaltyFilters = mutableListOf<PenaltyFilter>()
    var onPenalties: suspend (PenaltyFilter) -> List<PenaltyDto> = { listOf(penalty(1), penalty(2, PenaltyStatus.EXPIRED)) }
    val penaltyCalls = mutableListOf<PenaltyId>()
    var onPenalty: suspend (PenaltyId) -> PenaltyDetailsDto = { PenaltyDetailsDto(penalty(it.value), emptyList(), emptyList()) }
    val createdPenalties = mutableListOf<CreatePenaltyRequest>()
    var onCreatePenalty: suspend (CreatePenaltyRequest) -> PenaltyDto = { penalty(77) }
    val removals = mutableListOf<Pair<PenaltyId, PenaltyRemovalDto>>()
    var onRemovePenalty: suspend (PenaltyId, PenaltyRemovalDto) -> PenaltyDto = { id, removal ->
        penalty(id.value, PenaltyStatus.REMOVED, removal = removal)
    }

    val uploads = mutableListOf<Pair<PenaltyId, FileContent>>()
    var onUpload: suspend (PenaltyId, FileContent) -> AttachmentDto = { _, file -> attachment(90, file.fileName) }
    val downloads = mutableListOf<AttachmentId>()
    var onDownload: suspend (AttachmentId) -> FileContent = { FileContent("Приказ.pdf", "application/pdf", PDF_BYTES) }
    val deletedAttachments = mutableListOf<AttachmentId>()
    var onDeleteAttachment: suspend (AttachmentId) -> Unit = {}

    override suspend fun login(request: LoginRequest): LoginResponse {
        val user = onLogin(request)
        isSignedIn.value = true
        return LoginResponse("token", user)
    }

    override suspend fun logout() {
        isSignedIn.value = false
    }

    override suspend fun me(): UserDto = onMe()

    override suspend fun users(): List<UserDto> {
        usersCalls++
        return onUsers()
    }

    override suspend fun createUser(request: CreateUserRequest): UserDto {
        createdUsers += request
        return onCreateUser(request)
    }

    override suspend fun blockUser(id: UserId): UserDto {
        blockCalls += id to true
        return onSetBlocked(id, true)
    }

    override suspend fun unblockUser(id: UserId): UserDto {
        blockCalls += id to false
        return onSetBlocked(id, false)
    }
    override suspend fun departments(): List<DepartmentDto> {
        departmentsCalls++
        return onDepartments()
    }

    override suspend fun employees(query: String?, departmentId: DepartmentId?, includeArchived: Boolean): List<EmployeeDto> {
        val call = EmployeesQuery(query, departmentId, includeArchived)
        employeesQueries += call
        return onEmployees(call)
    }

    override suspend fun employee(id: EmployeeId): EmployeeDto {
        employeeCalls += id
        return onEmployee(id)
    }

    override suspend fun createEmployee(request: EmployeeRequest): EmployeeDto {
        createdEmployees += request
        return onCreateEmployee(request)
    }

    override suspend fun updateEmployee(id: EmployeeId, request: EmployeeRequest): EmployeeDto {
        updatedEmployees += id to request
        return onUpdateEmployee(id, request)
    }

    override suspend fun archiveEmployee(id: EmployeeId): EmployeeDto {
        archivedEmployees += id
        return employee(id.value, isArchived = true)
    }

    override suspend fun penalties(filter: PenaltyFilter): List<PenaltyDto> {
        penaltyFilters += filter
        return onPenalties(filter)
    }

    override suspend fun penalty(id: PenaltyId): PenaltyDetailsDto {
        penaltyCalls += id
        return onPenalty(id)
    }

    override suspend fun createPenalty(request: CreatePenaltyRequest): PenaltyDto {
        createdPenalties += request
        return onCreatePenalty(request)
    }

    override suspend fun removePenalty(id: PenaltyId, removal: PenaltyRemovalDto): PenaltyDto {
        removals += id to removal
        return onRemovePenalty(id, removal)
    }

    override suspend fun uploadAttachment(penaltyId: PenaltyId, file: FileContent): AttachmentDto {
        uploads += penaltyId to file
        return onUpload(penaltyId, file)
    }

    override suspend fun downloadAttachment(id: AttachmentId): FileContent {
        downloads += id
        return onDownload(id)
    }

    override suspend fun deleteAttachment(id: AttachmentId) {
        deletedAttachments += id
        onDeleteAttachment(id)
    }

    override suspend fun employeeCard(id: EmployeeId): EmployeeCardDto = unused()
    override suspend fun exportEmployeeCard(id: EmployeeId, format: ReportFormat): FileContent = unused()
    override suspend fun exportPenalties(filter: PenaltyFilter, format: ReportFormat): FileContent = unused()
    override suspend fun summaryReport(periodFrom: LocalDate, periodTo: LocalDate, departmentIds: List<DepartmentId>): SummaryReportDto = unused()
    override suspend fun reports(): List<ReportDto> = unused()
    override suspend fun createReport(request: CreateReportRequest): ReportDto = unused()
    override suspend fun downloadReport(id: ReportId): FileContent = unused()

    private fun unused(): Nothing = error("not used by these tests")
}
