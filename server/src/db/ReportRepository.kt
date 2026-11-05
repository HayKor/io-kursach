package ru.haykor.sudv.db

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import ru.haykor.sudv.contract.DepartmentDto
import ru.haykor.sudv.contract.ReportDto
import ru.haykor.sudv.domain.DepartmentId
import ru.haykor.sudv.domain.ReportFormat
import ru.haykor.sudv.domain.ReportId
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

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ReportRepository(private val transactor: Transactor) : IReportRepository {

    override suspend fun create(
        from: LocalDate,
        to: LocalDate,
        format: ReportFormat,
        storageKey: String,
        departmentIds: List<DepartmentId>,
        createdAt: Instant,
    ): ReportId = transactor.tx {
        val id = Reports.insertAndGetId {
            it[periodFrom] = from
            it[periodTo] = to
            it[Reports.format] = format
            it[Reports.storageKey] = storageKey
            it[Reports.createdAt] = createdAt
        }
        departmentIds.forEach { departmentId ->
            ReportDepartments.insert {
                it[reportId] = id.value
                it[ReportDepartments.departmentId] = departmentId.value
            }
        }
        ReportId(id.value)
    }

    override suspend fun findAll(): List<ReportDto> = transactor.tx {
        val rows = Reports.selectAll().orderBy(Reports.createdAt to SortOrder.DESC, Reports.id to SortOrder.DESC).toList()
        val departmentsByReport = departmentsByReport(rows.map { ReportId(it[Reports.id].value) })
        rows.map { it.toReportDto(departmentsByReport[ReportId(it[Reports.id].value)].orEmpty()) }
    }

    override suspend fun findById(id: ReportId): ReportRecord? = transactor.tx {
        val row = Reports.selectAll().where { Reports.id eq id.value }.singleOrNull()
        row?.let {
            val departments = departmentsByReport(listOf(id))[id].orEmpty()
            ReportRecord(it.toReportDto(departments), it[Reports.storageKey])
        }
    }

    private suspend fun departmentsByReport(ids: List<ReportId>): Map<ReportId, List<DepartmentDto>> {
        if (ids.isEmpty()) return emptyMap()
        return (ReportDepartments innerJoin Departments)
            .selectAll()
            .where { ReportDepartments.reportId inList ids.map { it.value } }
            .orderBy(Departments.name, SortOrder.ASC)
            .toList()
            .groupBy(
                keySelector = { ReportId(it[ReportDepartments.reportId].value) },
                valueTransform = { DepartmentDto(DepartmentId(it[Departments.id].value), it[Departments.name]) },
            )
    }

    private fun ResultRow.toReportDto(departments: List<DepartmentDto>) = ReportDto(
        id = ReportId(this[Reports.id].value),
        periodFrom = this[Reports.periodFrom],
        periodTo = this[Reports.periodTo],
        departments = departments,
        format = this[Reports.format],
        createdAt = this[Reports.createdAt],
    )
}
