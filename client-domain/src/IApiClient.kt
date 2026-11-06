package ru.haykor.sudv.client.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import ru.haykor.sudv.shared.contract.AttachmentDto
import ru.haykor.sudv.shared.contract.CreatePenaltyRequest
import ru.haykor.sudv.shared.contract.CreateReportRequest
import ru.haykor.sudv.shared.contract.CreateUserRequest
import ru.haykor.sudv.shared.contract.DepartmentDto
import ru.haykor.sudv.shared.contract.EmployeeCardDto
import ru.haykor.sudv.shared.contract.EmployeeDto
import ru.haykor.sudv.shared.contract.EmployeeRequest
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

/**
 * The only way the UI talks to the server; one method per route in [ru.haykor.sudv.shared.contract.ApiRoutes].
 * Every method throws [ApiException] when the server rejects the request and [NetworkException] when it cannot be reached.
 */
interface IApiClient {
    /** `true` while a token is stored; turns `false` after [logout] or once the server rejects the token. */
    val isSignedIn: Flow<Boolean>

    /** Stores the returned token, so later calls are authorized. */
    suspend fun login(request: LoginRequest): LoginResponse
    suspend fun logout()

    /** Restores the session after a restart: the stored token's owner. */
    suspend fun me(): UserDto

    suspend fun users(): List<UserDto>
    suspend fun createUser(request: CreateUserRequest): UserDto
    suspend fun blockUser(id: UserId): UserDto
    suspend fun unblockUser(id: UserId): UserDto

    suspend fun departments(): List<DepartmentDto>

    suspend fun employees(
        query: String? = null,
        departmentId: DepartmentId? = null,
        includeArchived: Boolean = false,
    ): List<EmployeeDto>
    suspend fun employee(id: EmployeeId): EmployeeDto
    suspend fun createEmployee(request: EmployeeRequest): EmployeeDto
    suspend fun updateEmployee(id: EmployeeId, request: EmployeeRequest): EmployeeDto
    suspend fun archiveEmployee(id: EmployeeId): EmployeeDto

    suspend fun penalties(filter: PenaltyFilter = PenaltyFilter()): List<PenaltyDto>
    suspend fun penalty(id: PenaltyId): PenaltyDetailsDto
    suspend fun createPenalty(request: CreatePenaltyRequest): PenaltyDto
    suspend fun removePenalty(id: PenaltyId, removal: PenaltyRemovalDto): PenaltyDto

    suspend fun uploadAttachment(penaltyId: PenaltyId, file: FileContent): AttachmentDto
    suspend fun downloadAttachment(id: AttachmentId): FileContent
    suspend fun deleteAttachment(id: AttachmentId)

    suspend fun employeeCard(id: EmployeeId): EmployeeCardDto
    suspend fun exportEmployeeCard(id: EmployeeId, format: ReportFormat): FileContent
    suspend fun exportPenalties(filter: PenaltyFilter, format: ReportFormat): FileContent

    /** An empty [departmentIds] means all departments. */
    suspend fun summaryReport(periodFrom: LocalDate, periodTo: LocalDate, departmentIds: List<DepartmentId>): SummaryReportDto
    suspend fun reports(): List<ReportDto>
    suspend fun createReport(request: CreateReportRequest): ReportDto
    suspend fun downloadReport(id: ReportId): FileContent
}
