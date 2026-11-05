package ru.haykor.sudv

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class PenaltyDto(
    val id: Long,
    val employee: EmployeeShortDto,
    val type: PenaltyType,
    val violationDate: LocalDate,
    val description: String,
    val orderNumber: String,
    val orderDate: LocalDate,
    /** Дата погашения. В БД не хранится, сервер вычисляет её по дате приказа. */
    val expiryDate: LocalDate,
    val status: PenaltyStatus,
    /** Реквизиты приказа о досрочном снятии, есть только у статуса [PenaltyStatus.REMOVED]. */
    val removal: PenaltyRemovalDto?,
)

/** Карточка взыскания: само взыскание, вложения и журнал смены статусов. */
@Serializable
data class PenaltyDetailsDto(
    val penalty: PenaltyDto,
    val attachments: List<AttachmentDto>,
    val events: List<StatusEventDto>,
)

@Serializable
data class CreatePenaltyRequest(
    val employeeId: Long,
    val type: PenaltyType,
    val violationDate: LocalDate,
    val description: String,
    val orderNumber: String,
    val orderDate: LocalDate,
)

/** Реквизиты приказа о досрочном снятии. Используется и в запросе на снятие, и в ответе. */
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
    val id: Long,
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

/**
 * Поиск и фильтрация реестра взысканий. Передаётся query-параметрами из [ApiParams],
 * незаданные (`null`) параметры не передаются. Условия объединяются через «И».
 */
data class PenaltyFilter(
    val status: PenaltyStatus? = null,
    val employeeName: String? = null,
    val departmentId: Long? = null,
    val orderDateFrom: LocalDate? = null,
    val orderDateTo: LocalDate? = null,
    val sort: PenaltySortField = PenaltySortField.ORDER_DATE,
    val direction: SortDirection = SortDirection.DESC,
)
