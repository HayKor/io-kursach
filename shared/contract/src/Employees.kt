package ru.haykor.sudv

import kotlinx.serialization.Serializable

@Serializable
data class DepartmentDto(
    val id: Long,
    val name: String,
)

@Serializable
data class EmployeeDto(
    val id: Long,
    val personnelNumber: String,
    val lastName: String,
    val firstName: String,
    val middleName: String?,
    val position: String,
    val department: DepartmentDto,
    val isArchived: Boolean,
)

/** Краткие сведения о сотруднике внутри других объектов (взыскание, учётная запись). */
@Serializable
data class EmployeeShortDto(
    val id: Long,
    val personnelNumber: String,
    val lastName: String,
    val firstName: String,
    val middleName: String?,
    val department: DepartmentDto,
)

/** Создание и редактирование сотрудника. */
@Serializable
data class EmployeeRequest(
    val personnelNumber: String,
    val lastName: String,
    val firstName: String,
    val middleName: String? = null,
    val position: String,
    val departmentId: Long,
)
