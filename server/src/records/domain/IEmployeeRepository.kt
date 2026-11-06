package ru.haykor.sudv.server.records.domain

import ru.haykor.sudv.shared.contract.DepartmentDto
import ru.haykor.sudv.shared.contract.EmployeeDto
import ru.haykor.sudv.shared.contract.EmployeeRequest
import ru.haykor.sudv.shared.domain.DepartmentId
import ru.haykor.sudv.shared.domain.EmployeeId

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
