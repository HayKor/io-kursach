package ru.haykor.sudv.server.records.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.concat
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import ru.haykor.sudv.server.core.database.Transactor
import ru.haykor.sudv.server.records.domain.AttachmentRecord
import ru.haykor.sudv.server.records.domain.IPenaltyRepository
import ru.haykor.sudv.shared.contract.AttachmentDto
import ru.haykor.sudv.shared.contract.CreatePenaltyRequest
import ru.haykor.sudv.shared.contract.DepartmentDto
import ru.haykor.sudv.shared.contract.EmployeeShortDto
import ru.haykor.sudv.shared.contract.PenaltyDto
import ru.haykor.sudv.shared.contract.PenaltyFilter
import ru.haykor.sudv.shared.contract.PenaltyRemovalDto
import ru.haykor.sudv.shared.contract.PenaltySortField
import ru.haykor.sudv.shared.contract.SortDirection
import ru.haykor.sudv.shared.contract.SummaryRowDto
import ru.haykor.sudv.shared.domain.AttachmentId
import ru.haykor.sudv.shared.domain.DepartmentId
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.PenaltyId
import ru.haykor.sudv.shared.domain.PenaltyRules
import ru.haykor.sudv.shared.domain.PenaltyStatus
import ru.haykor.sudv.shared.domain.PenaltyType
import kotlin.time.Instant

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PenaltyRepository(private val transactor: Transactor) : IPenaltyRepository {

    override suspend fun find(filter: PenaltyFilter): List<PenaltyDto> = transactor.tx {
        var condition: Op<Boolean> = Op.TRUE
        filter.status?.let { condition = condition and (Penalties.status eq it) }
        filter.departmentId?.let { condition = condition and (Employees.departmentId eq it.value) }
        filter.orderDateFrom?.let { condition = condition and (Penalties.orderDate greaterEq it) }
        filter.orderDateTo?.let { condition = condition and (Penalties.orderDate lessEq it) }

        val trimmedName = filter.employeeName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (trimmedName != null) {
            val pattern = LikePattern("%", '\\') + LikePattern.ofLiteral(trimmedName) + "%"
            val fullName = concat(" ", listOf(Employees.lastName, Employees.firstName, Employees.middleName))
            condition = condition and (fullName.lowerCase() like pattern)
        }

        val rows = penaltyQuery()
            .where { condition }
            .orderBy(*sortColumns(filter.sort, filter.direction))
            .toList()
            .map { it.toPenaltyDto() }
        attachRemovals(rows)
    }

    override suspend fun findById(id: PenaltyId): PenaltyDto? = transactor.tx {
        val dto = penaltyQuery().where { Penalties.id eq id.value }.singleOrNull()?.toPenaltyDto()
        dto?.let { attachRemovals(listOf(it)).single() }
    }

    override suspend fun findByEmployee(employeeId: EmployeeId): List<PenaltyDto> = transactor.tx {
        val rows = penaltyQuery()
            .where { Penalties.employeeId eq employeeId.value }
            .orderBy(Penalties.violationDate to SortOrder.ASC, Penalties.id to SortOrder.ASC)
            .toList()
            .map { it.toPenaltyDto() }
        attachRemovals(rows)
    }

    override suspend fun findActive(): List<Pair<PenaltyId, LocalDate>> = transactor.tx {
        Penalties
            .select(Penalties.id, Penalties.orderDate)
            .where { Penalties.status eq PenaltyStatus.ACTIVE }
            .toList()
            .map { PenaltyId(it[Penalties.id].value) to it[Penalties.orderDate] }
    }

    override suspend fun create(request: CreatePenaltyRequest): PenaltyId = transactor.tx {
        val id = Penalties.insertAndGetId {
            it[employeeId] = request.employeeId.value
            it[type] = request.type
            it[violationDate] = request.violationDate
            it[description] = request.description.trim()
            it[orderNumber] = request.orderNumber.trim()
            it[orderDate] = request.orderDate
            it[status] = PenaltyStatus.ACTIVE
        }
        PenaltyId(id.value)
    }

    override suspend fun changeStatus(id: PenaltyId, from: PenaltyStatus, to: PenaltyStatus): Boolean = transactor.tx {
        Penalties.update({ (Penalties.id eq id.value) and (Penalties.status eq from) }) { it[Penalties.status] = to } > 0
    }

    override suspend fun saveRemoval(id: PenaltyId, removal: PenaltyRemovalDto) {
        transactor.tx {
            PenaltyRemovals.insert {
                it[penaltyId] = id.value
                it[orderNumber] = removal.orderNumber.trim()
                it[orderDate] = removal.orderDate
            }
        }
    }

    override suspend fun findAttachments(penaltyId: PenaltyId): List<AttachmentDto> = transactor.tx {
        Attachments
            .selectAll()
            .where { Attachments.penaltyId eq penaltyId.value }
            .orderBy(Attachments.uploadedAt to SortOrder.ASC, Attachments.id to SortOrder.ASC)
            .toList()
            .map { it.toAttachmentDto() }
    }

    override suspend fun findAttachment(id: AttachmentId): AttachmentRecord? = transactor.tx {
        Attachments.selectAll().where { Attachments.id eq id.value }.singleOrNull()?.toAttachmentRecord()
    }

    override suspend fun addAttachment(
        penaltyId: PenaltyId,
        fileName: String,
        contentType: String,
        sizeBytes: Long,
        storageKey: String,
        uploadedAt: Instant,
    ): AttachmentId = transactor.tx {
        val id = Attachments.insertAndGetId {
            it[Attachments.penaltyId] = penaltyId.value
            it[Attachments.fileName] = fileName
            it[Attachments.contentType] = contentType
            it[Attachments.sizeBytes] = sizeBytes.toInt()
            it[Attachments.storageKey] = storageKey
            it[Attachments.uploadedAt] = uploadedAt
        }
        AttachmentId(id.value)
    }

    override suspend fun deleteAttachment(id: AttachmentId) {
        transactor.tx { Attachments.deleteWhere { Attachments.id eq id.value } }
    }

    override suspend fun countByDepartment(from: LocalDate, to: LocalDate, departmentIds: List<DepartmentId>): List<SummaryRowDto> = transactor.tx {
        val departmentValues = departmentIds.map { it.value }
        val departments = Departments
            .selectAll()
            .where { Departments.id inList departmentValues }
            .orderBy(Departments.name, SortOrder.ASC)
            .toList()
            .map { DepartmentDto(DepartmentId(it[Departments.id].value), it[Departments.name]) }

        // One grouped query for every requested department's counts, instead of one query per department.
        val penaltyCount = Penalties.id.count()
        val counts = (Penalties innerJoin Employees innerJoin Departments)
            .select(Departments.id, Penalties.type, penaltyCount)
            .where {
                (Penalties.violationDate greaterEq from) and
                    (Penalties.violationDate lessEq to) and
                    (Employees.departmentId inList departmentValues)
            }
            .groupBy(Departments.id, Penalties.type)
            .toList()
            .associate { (DepartmentId(it[Departments.id].value) to it[Penalties.type]) to it[penaltyCount] }

        departments.map { department ->
            SummaryRowDto(
                department = department,
                remarkCount = (counts[department.id to PenaltyType.REMARK] ?: 0L).toInt(),
                reprimandCount = (counts[department.id to PenaltyType.REPRIMAND] ?: 0L).toInt(),
            )
        }
    }

    private fun penaltyQuery() = (Penalties innerJoin Employees innerJoin Departments).selectAll()

    private suspend fun attachRemovals(dtos: List<PenaltyDto>): List<PenaltyDto> {
        if (dtos.isEmpty()) return dtos
        val removedIds = dtos.filter { it.status == PenaltyStatus.REMOVED }.map { it.id.value }
        if (removedIds.isEmpty()) return dtos

        val removals = PenaltyRemovals
            .selectAll()
            .where { PenaltyRemovals.penaltyId inList removedIds }
            .toList()
            .associate { PenaltyId(it[PenaltyRemovals.penaltyId].value) to it.toRemovalDto() }
        return dtos.map { it.copy(removal = removals[it.id]) }
    }

    private fun sortColumns(sort: PenaltySortField, direction: SortDirection): Array<Pair<Expression<*>, SortOrder>> {
        val order = if (direction == SortDirection.ASC) SortOrder.ASC else SortOrder.DESC
        val primary: List<Pair<Expression<*>, SortOrder>> = when (sort) {
            PenaltySortField.EMPLOYEE_NAME -> listOf(
                Employees.lastName to order,
                Employees.firstName to order,
                Employees.middleName to order,
            )

            PenaltySortField.VIOLATION_DATE -> listOf(Penalties.violationDate to order)
            PenaltySortField.ORDER_DATE -> listOf(Penalties.orderDate to order)
            PenaltySortField.STATUS -> listOf(Penalties.status to order)
        }
        return (primary + (Penalties.id to SortOrder.ASC)).toTypedArray()
    }

    private fun ResultRow.toPenaltyDto(): PenaltyDto {
        val orderDate = this[Penalties.orderDate]
        return PenaltyDto(
            id = PenaltyId(this[Penalties.id].value),
            employee = toEmployeeShortDto(),
            type = this[Penalties.type],
            violationDate = this[Penalties.violationDate],
            description = this[Penalties.description],
            orderNumber = this[Penalties.orderNumber],
            orderDate = orderDate,
            expiryDate = PenaltyRules.expiryDate(orderDate),
            status = this[Penalties.status],
            removal = null,
        )
    }

    private fun ResultRow.toEmployeeShortDto() = EmployeeShortDto(
        id = EmployeeId(this[Employees.id].value),
        personnelNumber = this[Employees.personnelNumber],
        lastName = this[Employees.lastName],
        firstName = this[Employees.firstName],
        middleName = this[Employees.middleName],
        department = DepartmentDto(DepartmentId(this[Departments.id].value), this[Departments.name]),
    )

    private fun ResultRow.toRemovalDto() = PenaltyRemovalDto(
        orderNumber = this[PenaltyRemovals.orderNumber],
        orderDate = this[PenaltyRemovals.orderDate],
    )

    private fun ResultRow.toAttachmentDto() = AttachmentDto(
        id = AttachmentId(this[Attachments.id].value),
        fileName = this[Attachments.fileName],
        contentType = this[Attachments.contentType],
        sizeBytes = this[Attachments.sizeBytes].toLong(),
        uploadedAt = this[Attachments.uploadedAt],
    )

    private fun ResultRow.toAttachmentRecord() = AttachmentRecord(
        dto = toAttachmentDto(),
        penaltyId = PenaltyId(this[Attachments.penaltyId].value),
        storageKey = this[Attachments.storageKey],
    )
}
