package ru.haykor.sudv.server.records.domain

import kotlinx.datetime.LocalDate
import ru.haykor.sudv.shared.contract.AttachmentDto
import ru.haykor.sudv.shared.contract.CreatePenaltyRequest
import ru.haykor.sudv.shared.contract.PenaltyDto
import ru.haykor.sudv.shared.contract.PenaltyFilter
import ru.haykor.sudv.shared.contract.PenaltyRemovalDto
import ru.haykor.sudv.shared.contract.SummaryRowDto
import ru.haykor.sudv.shared.domain.AttachmentId
import ru.haykor.sudv.shared.domain.DepartmentId
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.PenaltyId
import ru.haykor.sudv.shared.domain.PenaltyRules
import ru.haykor.sudv.shared.domain.PenaltyStatus
import kotlin.time.Instant

/** [storageKey] and [penaltyId] are internal fields not exposed through [AttachmentDto]. */
data class AttachmentRecord(val dto: AttachmentDto, val penaltyId: PenaltyId, val storageKey: String)

interface IPenaltyRepository {
    suspend fun find(filter: PenaltyFilter): List<PenaltyDto>
    suspend fun findById(id: PenaltyId): PenaltyDto?

    /** Sorted by violation_date, then id, ascending. */
    suspend fun findByEmployee(employeeId: EmployeeId): List<PenaltyDto>

    /** Id and order_date of ACTIVE penalties; whether one is due is decided by [PenaltyRules.isDue]. */
    suspend fun findActive(): List<Pair<PenaltyId, LocalDate>>

    /** Status is set to [PenaltyStatus.ACTIVE]. */
    suspend fun create(request: CreatePenaltyRequest): PenaltyId

    /** Single `UPDATE ... WHERE id = ? AND status = from`; returns whether a row actually changed. */
    suspend fun changeStatus(id: PenaltyId, from: PenaltyStatus, to: PenaltyStatus): Boolean
    suspend fun saveRemoval(id: PenaltyId, removal: PenaltyRemovalDto)

    /** Sorted by uploaded_at, then id. */
    suspend fun findAttachments(penaltyId: PenaltyId): List<AttachmentDto>
    suspend fun findAttachment(id: AttachmentId): AttachmentRecord?
    suspend fun addAttachment(
        penaltyId: PenaltyId,
        fileName: String,
        contentType: String,
        sizeBytes: Long,
        storageKey: String,
        uploadedAt: Instant,
    ): AttachmentId

    suspend fun deleteAttachment(id: AttachmentId)

    /** All [departmentIds] are present even with zero counts; counted by violation_date within [from, to] inclusive, sorted by name. */
    suspend fun countByDepartment(from: LocalDate, to: LocalDate, departmentIds: List<DepartmentId>): List<SummaryRowDto>
}
