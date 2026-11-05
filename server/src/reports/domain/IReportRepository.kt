package ru.haykor.sudv.server.reports.domain

import kotlinx.datetime.LocalDate
import ru.haykor.sudv.shared.contract.ReportDto
import ru.haykor.sudv.shared.domain.DepartmentId
import ru.haykor.sudv.shared.domain.ReportFormat
import ru.haykor.sudv.shared.domain.ReportId
import kotlin.time.Instant

/** [storageKey] is an internal field not exposed through [ReportDto]. */
data class ReportRecord(val dto: ReportDto, val storageKey: String)

interface IReportRepository {
    /** [departmentIds] is the actual set the report covers; the caller has already resolved an empty request to "all departments". */
    suspend fun create(
        from: LocalDate,
        to: LocalDate,
        format: ReportFormat,
        storageKey: String,
        departmentIds: List<DepartmentId>,
        createdAt: Instant,
    ): ReportId

    /** Newest first. */
    suspend fun findAll(): List<ReportDto>
    suspend fun findById(id: ReportId): ReportRecord?
}
