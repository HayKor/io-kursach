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
import ru.haykor.sudv.shared.domain.ReportFormat
import ru.haykor.sudv.shared.domain.ReportId
import ru.haykor.sudv.shared.domain.UserId
import ru.haykor.sudv.shared.domain.UserRole

val TEST_USER = UserDto(
    id = UserId(1),
    login = "hr",
    role = UserRole.HR,
    isBlocked = false,
    employee = EmployeeShortDto(EmployeeId(1), "EMP-1", "Иванов", "Иван", null, DepartmentDto(DepartmentId(1), "ИТ-отдел")),
)

/** Mimics the real client's session handling: [login] stores a token, [logout] clears it. Other routes are not used. */
class FakeApiClient(signedIn: Boolean) : IApiClient {
    override val isSignedIn = MutableStateFlow(signedIn)
    var onLogin: suspend (LoginRequest) -> UserDto = { TEST_USER }
    var onMe: suspend () -> UserDto = { TEST_USER }

    override suspend fun login(request: LoginRequest): LoginResponse {
        val user = onLogin(request)
        isSignedIn.value = true
        return LoginResponse("token", user)
    }

    override suspend fun logout() {
        isSignedIn.value = false
    }

    override suspend fun me(): UserDto = onMe()

    override suspend fun users(): List<UserDto> = unused()
    override suspend fun createUser(request: CreateUserRequest): UserDto = unused()
    override suspend fun blockUser(id: UserId): UserDto = unused()
    override suspend fun unblockUser(id: UserId): UserDto = unused()
    override suspend fun departments(): List<DepartmentDto> = unused()
    override suspend fun employees(query: String?, departmentId: DepartmentId?, includeArchived: Boolean): List<EmployeeDto> = unused()
    override suspend fun employee(id: EmployeeId): EmployeeDto = unused()
    override suspend fun createEmployee(request: EmployeeRequest): EmployeeDto = unused()
    override suspend fun updateEmployee(id: EmployeeId, request: EmployeeRequest): EmployeeDto = unused()
    override suspend fun archiveEmployee(id: EmployeeId): EmployeeDto = unused()
    override suspend fun penalties(filter: PenaltyFilter): List<PenaltyDto> = unused()
    override suspend fun penalty(id: PenaltyId): PenaltyDetailsDto = unused()
    override suspend fun createPenalty(request: CreatePenaltyRequest): PenaltyDto = unused()
    override suspend fun removePenalty(id: PenaltyId, removal: PenaltyRemovalDto): PenaltyDto = unused()
    override suspend fun uploadAttachment(penaltyId: PenaltyId, file: FileContent): AttachmentDto = unused()
    override suspend fun downloadAttachment(id: AttachmentId): FileContent = unused()
    override suspend fun deleteAttachment(id: AttachmentId) = unused()
    override suspend fun employeeCard(id: EmployeeId): EmployeeCardDto = unused()
    override suspend fun exportEmployeeCard(id: EmployeeId, format: ReportFormat): FileContent = unused()
    override suspend fun exportPenalties(filter: PenaltyFilter, format: ReportFormat): FileContent = unused()
    override suspend fun summaryReport(periodFrom: LocalDate, periodTo: LocalDate, departmentIds: List<DepartmentId>): SummaryReportDto = unused()
    override suspend fun reports(): List<ReportDto> = unused()
    override suspend fun createReport(request: CreateReportRequest): ReportDto = unused()
    override suspend fun downloadReport(id: ReportId): FileContent = unused()

    private fun unused(): Nothing = error("not used by these tests")
}
