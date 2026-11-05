package ru.haykor.sudv

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Персональная карточка нарушений: взыскания сотрудника в хронологическом порядке. */
@Serializable
data class EmployeeCardDto(
    val employee: EmployeeDto,
    val penalties: List<PenaltyDto>,
)

/** Сводный отчёт по подразделениям за период. */
@Serializable
data class SummaryReportDto(
    val periodFrom: LocalDate,
    val periodTo: LocalDate,
    val rows: List<SummaryRowDto>,
)

@Serializable
data class SummaryRowDto(
    val department: DepartmentDto,
    val remarkCount: Int,
    val reprimandCount: Int,
) {
    val totalCount: Int get() = remarkCount + reprimandCount
}

/** Формирование сводного отчёта с сохранением в архив. */
@Serializable
data class CreateReportRequest(
    val periodFrom: LocalDate,
    val periodTo: LocalDate,
    val departmentIds: List<Long>,
    val format: ReportFormat,
)

/** Отчёт из архива с параметрами формирования. */
@Serializable
data class ReportDto(
    val id: Long,
    val periodFrom: LocalDate,
    val periodTo: LocalDate,
    val departments: List<DepartmentDto>,
    val format: ReportFormat,
    val createdAt: Instant,
)
