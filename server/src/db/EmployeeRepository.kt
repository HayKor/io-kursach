package ru.haykor.sudv.db

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.concat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import ru.haykor.sudv.contract.DepartmentDto
import ru.haykor.sudv.contract.EmployeeDto
import ru.haykor.sudv.contract.EmployeeRequest
import ru.haykor.sudv.domain.DepartmentId
import ru.haykor.sudv.domain.EmployeeId

interface IEmployeeRepository {
    /** Sorted by name. */
    suspend fun findAllDepartments(): List<DepartmentDto>
    suspend fun findDepartment(id: DepartmentId): DepartmentDto?
    suspend fun findDepartmentByName(name: String): DepartmentDto?
    suspend fun createDepartment(name: String): DepartmentId

    /** Sorted by last, first, middle name. [query] matches full name or personnel number, case-insensitively. */
    suspend fun find(query: String?, departmentId: DepartmentId?, includeArchived: Boolean): List<EmployeeDto>
    suspend fun findById(id: EmployeeId): EmployeeDto?
    suspend fun findByPersonnelNumber(number: String): EmployeeDto?
    suspend fun create(request: EmployeeRequest): EmployeeId
    suspend fun update(id: EmployeeId, request: EmployeeRequest)
    suspend fun setArchived(id: EmployeeId)
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class EmployeeRepository(private val transactor: Transactor) : IEmployeeRepository {

    override suspend fun findAllDepartments(): List<DepartmentDto> = transactor.tx {
        Departments.selectAll().orderBy(Departments.name, SortOrder.ASC).toList().map { it.toDepartmentDto() }
    }

    override suspend fun findDepartment(id: DepartmentId): DepartmentDto? = transactor.tx {
        Departments.selectAll().where { Departments.id eq id.value }.singleOrNull()?.toDepartmentDto()
    }

    override suspend fun findDepartmentByName(name: String): DepartmentDto? = transactor.tx {
        Departments.selectAll().where { Departments.name eq name }.singleOrNull()?.toDepartmentDto()
    }

    override suspend fun createDepartment(name: String): DepartmentId = transactor.tx {
        DepartmentId(Departments.insertAndGetId { it[Departments.name] = name }.value)
    }

    override suspend fun find(query: String?, departmentId: DepartmentId?, includeArchived: Boolean): List<EmployeeDto> = transactor.tx {
        var condition: Op<Boolean> = if (includeArchived) Op.TRUE else (Employees.isArchived eq false)
        departmentId?.let { condition = condition and (Employees.departmentId eq it.value) }

        val trimmedQuery = query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (trimmedQuery != null) {
            val pattern = LikePattern("%", '\\') + LikePattern.ofLiteral(trimmedQuery) + "%"
            val fullName = concat(" ", listOf(Employees.lastName, Employees.firstName, Employees.middleName))
            condition = condition and ((fullName.lowerCase() like pattern) or (Employees.personnelNumber.lowerCase() like pattern))
        }

        (Employees innerJoin Departments)
            .selectAll()
            .where { condition }
            .orderBy(Employees.lastName to SortOrder.ASC, Employees.firstName to SortOrder.ASC, Employees.middleName to SortOrder.ASC)
            .toList()
            .map { it.toEmployeeDto() }
    }

    override suspend fun findById(id: EmployeeId): EmployeeDto? = transactor.tx {
        (Employees innerJoin Departments).selectAll().where { Employees.id eq id.value }.singleOrNull()?.toEmployeeDto()
    }

    override suspend fun findByPersonnelNumber(number: String): EmployeeDto? = transactor.tx {
        (Employees innerJoin Departments).selectAll().where { Employees.personnelNumber eq number }.singleOrNull()?.toEmployeeDto()
    }

    override suspend fun create(request: EmployeeRequest): EmployeeId = transactor.tx {
        val id = Employees.insertAndGetId {
            it[personnelNumber] = request.personnelNumber.trim()
            it[lastName] = request.lastName.trim()
            it[firstName] = request.firstName.trim()
            it[middleName] = request.middleName?.trim()?.takeIf { middle -> middle.isNotEmpty() }
            it[position] = request.position.trim()
            it[departmentId] = request.departmentId.value
        }
        EmployeeId(id.value)
    }

    override suspend fun update(id: EmployeeId, request: EmployeeRequest) {
        transactor.tx {
            Employees.update({ Employees.id eq id.value }) {
                it[personnelNumber] = request.personnelNumber.trim()
                it[lastName] = request.lastName.trim()
                it[firstName] = request.firstName.trim()
                it[middleName] = request.middleName?.trim()?.takeIf { middle -> middle.isNotEmpty() }
                it[position] = request.position.trim()
                it[departmentId] = request.departmentId.value
            }
        }
    }

    override suspend fun setArchived(id: EmployeeId) {
        transactor.tx { Employees.update({ Employees.id eq id.value }) { it[isArchived] = true } }
    }

    private fun ResultRow.toDepartmentDto() = DepartmentDto(DepartmentId(this[Departments.id].value), this[Departments.name])

    private fun ResultRow.toEmployeeDto() = EmployeeDto(
        id = EmployeeId(this[Employees.id].value),
        personnelNumber = this[Employees.personnelNumber],
        lastName = this[Employees.lastName],
        firstName = this[Employees.firstName],
        middleName = this[Employees.middleName],
        position = this[Employees.position],
        department = toDepartmentDto(),
        isArchived = this[Employees.isArchived],
    )
}
