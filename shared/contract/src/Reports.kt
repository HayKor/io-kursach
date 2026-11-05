package ru.haykor.sudv.contract

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import ru.haykor.sudv.domain.DepartmentId
import ru.haykor.sudv.domain.ReportFormat
import ru.haykor.sudv.domain.ReportId
import kotlin.time.Instant

/** [penalties] are in chronological order. */
@Serializable
data class EmployeeCardDto(
    val employee: EmployeeDto,
    val penalties: List<PenaltyDto>,
)

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

/** Generates the summary report file and saves it to the archive. */
@Serializable
data class CreateReportRequest(
    val periodFrom: LocalDate,
    val periodTo: LocalDate,
    val departmentIds: List<DepartmentId>,
    val format: ReportFormat,
)

@Serializable
data class ReportDto(
    val id: ReportId,
    val periodFrom: LocalDate,
    val periodTo: LocalDate,
    val departments: List<DepartmentDto>,
    val format: ReportFormat,
    val createdAt: Instant,
)
