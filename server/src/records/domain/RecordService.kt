package ru.haykor.sudv.server.records.domain

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.slf4j.LoggerFactory
import ru.haykor.sudv.server.core.database.Transactor
import ru.haykor.sudv.server.core.error.ApiException
import ru.haykor.sudv.server.core.files.IFileStorage
import ru.haykor.sudv.server.core.security.CurrentUser
import ru.haykor.sudv.shared.contract.AttachmentDto
import ru.haykor.sudv.shared.contract.CreatePenaltyRequest
import ru.haykor.sudv.shared.contract.DepartmentDto
import ru.haykor.sudv.shared.contract.EmployeeDto
import ru.haykor.sudv.shared.contract.EmployeeRequest
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.contract.PenaltyDetailsDto
import ru.haykor.sudv.shared.contract.PenaltyDto
import ru.haykor.sudv.shared.contract.PenaltyFilter
import ru.haykor.sudv.shared.domain.AttachmentId
import ru.haykor.sudv.shared.domain.DepartmentId
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.PenaltyId
import ru.haykor.sudv.shared.domain.PenaltyStatus
import ru.haykor.sudv.shared.domain.UserRole
import ru.haykor.sudv.shared.domain.Validation
import kotlin.time.Clock

interface IRecordService {
    suspend fun departments(): List<DepartmentDto>

    /** For MANAGER, [departmentId] is forced to [CurrentUser.departmentId]; a different value is [ErrorCode.FORBIDDEN]. */
    suspend fun findEmployees(actor: CurrentUser, query: String?, departmentId: DepartmentId?, includeArchived: Boolean): List<EmployeeDto>
    suspend fun getEmployee(actor: CurrentUser, id: EmployeeId): EmployeeDto
    suspend fun createEmployee(request: EmployeeRequest): EmployeeDto
    suspend fun updateEmployee(id: EmployeeId, request: EmployeeRequest): EmployeeDto

    /** Archiving an already archived employee is a no-op. */
    suspend fun archiveEmployee(id: EmployeeId): EmployeeDto

    /** For MANAGER, [PenaltyFilter.departmentId] is forced the same way as in [findEmployees]. */
    suspend fun findPenalties(actor: CurrentUser, filter: PenaltyFilter): List<PenaltyDto>
    suspend fun getPenalty(actor: CurrentUser, id: PenaltyId): PenaltyDetailsDto
    suspend fun createPenalty(request: CreatePenaltyRequest): PenaltyDto

    /** Validates size and format (see [ru.haykor.sudv.shared.domain.AttachmentRules]); the penalty's department is not checked here. */
    suspend fun addAttachment(penaltyId: PenaltyId, fileName: String, contentType: String, bytes: ByteArray): AttachmentDto

    /** For MANAGER, a foreign department's attachment is [ErrorCode.FORBIDDEN]. */
    suspend fun getAttachment(actor: CurrentUser, id: AttachmentId): StoredFile
    suspend fun deleteAttachment(id: AttachmentId)
}

// Not a data class: generated equals/hashCode would compare the ByteArray by reference.
class StoredFile(val fileName: String, val contentType: String, val bytes: ByteArray)

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RecordService(
    private val employeeRepository: IEmployeeRepository,
    private val penaltyRepository: IPenaltyRepository,
    private val eventLogRepository: IEventLogRepository,
    private val fileStorage: IFileStorage,
    private val transactor: Transactor,
    private val clock: Clock,
    private val timeZone: TimeZone,
) : IRecordService {
    private val logger = LoggerFactory.getLogger(RecordService::class.java)

    override suspend fun departments(): List<DepartmentDto> = employeeRepository.findAllDepartments()

    override suspend fun findEmployees(
        actor: CurrentUser,
        query: String?,
        departmentId: DepartmentId?,
        includeArchived: Boolean,
    ): List<EmployeeDto> {
        val scopedDepartmentId = actor.scopeDepartment(departmentId)
        return employeeRepository.find(query, scopedDepartmentId, includeArchived)
    }

    override suspend fun getEmployee(actor: CurrentUser, id: EmployeeId): EmployeeDto {
        val employee = employeeRepository.findById(id) ?: throw ApiException(ErrorCode.NOT_FOUND, "Сотрудник не найден")
        actor.requireOwnDepartment(employee.department.id)
        return employee
    }

    override suspend fun createEmployee(request: EmployeeRequest): EmployeeDto {
        validateEmployee(request)
        val id = employeeRepository.create(request)
        return employeeRepository.findById(id) ?: error("Employee $id disappeared right after creation")
    }

    override suspend fun updateEmployee(id: EmployeeId, request: EmployeeRequest): EmployeeDto {
        employeeRepository.findById(id) ?: throw ApiException(ErrorCode.NOT_FOUND, "Сотрудник не найден")
        validateEmployee(request, excludingId = id)
        employeeRepository.update(id, request)
        return employeeRepository.findById(id) ?: error("Employee $id disappeared right after update")
    }

    override suspend fun archiveEmployee(id: EmployeeId): EmployeeDto {
        val employee = employeeRepository.findById(id) ?: throw ApiException(ErrorCode.NOT_FOUND, "Сотрудник не найден")
        if (!employee.isArchived) employeeRepository.setArchived(id)
        return employeeRepository.findById(id) ?: error("Employee $id disappeared right after archiving")
    }

    override suspend fun findPenalties(actor: CurrentUser, filter: PenaltyFilter): List<PenaltyDto> {
        val scopedDepartmentId = actor.scopeDepartment(filter.departmentId)
        return penaltyRepository.find(filter.copy(departmentId = scopedDepartmentId))
    }

    override suspend fun getPenalty(actor: CurrentUser, id: PenaltyId): PenaltyDetailsDto {
        val penalty = penaltyRepository.findById(id) ?: throw ApiException(ErrorCode.NOT_FOUND, "Взыскание не найдено")
        actor.requireOwnDepartment(penalty.employee.department.id)
        val attachments = penaltyRepository.findAttachments(id)
        val events = eventLogRepository.findByPenalty(id)
        return PenaltyDetailsDto(penalty = penalty, attachments = attachments, events = events)
    }

    override suspend fun createPenalty(request: CreatePenaltyRequest): PenaltyDto {
        val employee = employeeRepository.findById(request.employeeId)
        if (employee == null || employee.isArchived) {
            throw ApiException(ErrorCode.VALIDATION_FAILED, "Сотрудник не найден или в архиве")
        }

        val today = clock.todayIn(timeZone)
        val errors = Validation.penalty(request.violationDate, request.orderDate, request.description, request.orderNumber, today)
        if (errors.isNotEmpty()) throw ApiException(ErrorCode.VALIDATION_FAILED, errors.joinToString("; "))

        return transactor.tx {
            val id = penaltyRepository.create(request)
            eventLogRepository.add(id, PenaltyStatus.ACTIVE, clock.now())
            penaltyRepository.findById(id) ?: error("Penalty $id disappeared right after creation")
        }
    }

    override suspend fun addAttachment(penaltyId: PenaltyId, fileName: String, contentType: String, bytes: ByteArray): AttachmentDto {
        penaltyRepository.findById(penaltyId) ?: throw ApiException(ErrorCode.NOT_FOUND, "Взыскание не найдено")

        val storageKey = fileStorage.save(contentType, bytes, validateAttachment = true)
        val uploadedAt = clock.now()
        return try {
            val id = penaltyRepository.addAttachment(penaltyId, fileName, contentType, bytes.size.toLong(), storageKey, uploadedAt)
            AttachmentDto(id, fileName, contentType, bytes.size.toLong(), uploadedAt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fileStorage.delete(storageKey)
            throw e
        }
    }

    override suspend fun getAttachment(actor: CurrentUser, id: AttachmentId): StoredFile {
        val record = penaltyRepository.findAttachment(id) ?: throw ApiException(ErrorCode.NOT_FOUND, "Вложение не найдено")
        val penalty = penaltyRepository.findById(record.penaltyId) ?: error("Penalty ${record.penaltyId} missing for attachment $id")
        actor.requireOwnDepartment(penalty.employee.department.id)

        val bytes = fileStorage.read(record.storageKey)
        if (bytes == null) {
            logger.warn("Attachment file missing on disk: id={}, key={}", id, record.storageKey)
            throw ApiException(ErrorCode.NOT_FOUND, "Файл вложения не найден")
        }
        return StoredFile(record.dto.fileName, record.dto.contentType, bytes)
    }

    override suspend fun deleteAttachment(id: AttachmentId) {
        val record = penaltyRepository.findAttachment(id) ?: throw ApiException(ErrorCode.NOT_FOUND, "Вложение не найдено")
        penaltyRepository.deleteAttachment(id)
        try {
            fileStorage.delete(record.storageKey)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // An orphan file on disk is harmless; a dangling DB row that still points at a deleted file is not.
            logger.warn("Failed to delete attachment file: key={}", record.storageKey, e)
        }
    }

    private suspend fun validateEmployee(request: EmployeeRequest, excludingId: EmployeeId? = null) {
        val errors = Validation.employee(request.personnelNumber, request.lastName, request.firstName, request.middleName, request.position)
        if (errors.isNotEmpty()) throw ApiException(ErrorCode.VALIDATION_FAILED, errors.joinToString("; "))

        employeeRepository.findDepartment(request.departmentId) ?: throw ApiException(ErrorCode.VALIDATION_FAILED, "Подразделение не найдено")

        val existing = employeeRepository.findByPersonnelNumber(request.personnelNumber.trim())
        if (existing != null && existing.id != excludingId) {
            throw ApiException(ErrorCode.DUPLICATE_PERSONNEL_NUMBER, "Табельный номер уже используется")
        }
    }

    /** MANAGER is pinned to their own department; a different explicit value is forbidden, not silently ignored. */
    private fun CurrentUser.scopeDepartment(requested: DepartmentId?): DepartmentId? {
        if (role != UserRole.MANAGER) return requested
        if (requested != null && requested != departmentId) {
            throw ApiException(ErrorCode.FORBIDDEN, "Недостаточно прав")
        }
        return departmentId
    }

    private fun CurrentUser.requireOwnDepartment(employeeDepartmentId: DepartmentId) {
        if (role == UserRole.MANAGER && employeeDepartmentId != departmentId) {
            throw ApiException(ErrorCode.FORBIDDEN, "Недостаточно прав")
        }
    }
}
