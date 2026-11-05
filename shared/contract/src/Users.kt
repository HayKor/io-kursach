package ru.haykor.sudv

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: UserId,
    val login: String,
    val role: UserRole,
    val isBlocked: Boolean,
    val employee: EmployeeShortDto,
)

@Serializable
data class CreateUserRequest(
    val login: String,
    val password: String,
    val role: UserRole,
    val employeeId: EmployeeId,
)
