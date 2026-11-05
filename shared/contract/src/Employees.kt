package ru.haykor.sudv.contract

import kotlinx.serialization.Serializable
import ru.haykor.sudv.domain.DepartmentId
import ru.haykor.sudv.domain.EmployeeId

@Serializable
data class DepartmentDto(
    val id: DepartmentId,
    val name: String,
)

@Serializable
data class EmployeeDto(
    val id: EmployeeId,
    val personnelNumber: String,
    val lastName: String,
    val firstName: String,
    val middleName: String?,
    val position: String,
    val department: DepartmentDto,
    val isArchived: Boolean,
)

@Serializable
data class EmployeeShortDto(
    val id: EmployeeId,
    val personnelNumber: String,
    val lastName: String,
    val firstName: String,
    val middleName: String?,
    val department: DepartmentDto,
)

/** Used for both creating and updating an employee. */
@Serializable
data class EmployeeRequest(
    val personnelNumber: String,
    val lastName: String,
    val firstName: String,
    val middleName: String? = null,
    val position: String,
    val departmentId: DepartmentId,
)
