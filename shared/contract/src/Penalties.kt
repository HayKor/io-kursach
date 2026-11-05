package ru.haykor.sudv.contract

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import ru.haykor.sudv.domain.AttachmentId
import ru.haykor.sudv.domain.DepartmentId
import ru.haykor.sudv.domain.EmployeeId
import ru.haykor.sudv.domain.PenaltyId
import ru.haykor.sudv.domain.PenaltyStatus
import ru.haykor.sudv.domain.PenaltyType
import kotlin.time.Instant

@Serializable
data class PenaltyDto(
    val id: PenaltyId,
    val employee: EmployeeShortDto,
    val type: PenaltyType,
    val violationDate: LocalDate,
    val description: String,
    val orderNumber: String,
    val orderDate: LocalDate,
    /** Not stored in the DB; the server computes it from [orderDate]. */
    val expiryDate: LocalDate,
    val status: PenaltyStatus,
    /** Present only when [status] is [PenaltyStatus.REMOVED]. */
    val removal: PenaltyRemovalDto?,
)

@Serializable
data class PenaltyDetailsDto(
    val penalty: PenaltyDto,
    val attachments: List<AttachmentDto>,
    val events: List<StatusEventDto>,
)

@Serializable
data class CreatePenaltyRequest(
    val employeeId: EmployeeId,
    val type: PenaltyType,
    val violationDate: LocalDate,
    val description: String,
    val orderNumber: String,
    val orderDate: LocalDate,
)

/** Used both as the early removal request body and in [PenaltyDto]. */
@Serializable
data class PenaltyRemovalDto(
    val orderNumber: String,
    val orderDate: LocalDate,
)

@Serializable
data class StatusEventDto(
    val status: PenaltyStatus,
    val changedAt: Instant,
)

@Serializable
data class AttachmentDto(
    val id: AttachmentId,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val uploadedAt: Instant,
)

enum class PenaltySortField {
    EMPLOYEE_NAME,
    VIOLATION_DATE,
    ORDER_DATE,
    STATUS,
}

enum class SortDirection {
    ASC,
    DESC,
}

/** Sent as query parameters (see [ApiParams]); `null` values are omitted, conditions are combined with AND. */
data class PenaltyFilter(
    val status: PenaltyStatus? = null,
    val employeeName: String? = null,
    val departmentId: DepartmentId? = null,
    val orderDateFrom: LocalDate? = null,
    val orderDateTo: LocalDate? = null,
    val sort: PenaltySortField = PenaltySortField.ORDER_DATE,
    val direction: SortDirection = SortDirection.DESC,
)
