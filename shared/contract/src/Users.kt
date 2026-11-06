package ru.haykor.sudv.shared.contract

import kotlinx.serialization.Serializable
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.UserId
import ru.haykor.sudv.shared.domain.UserRole

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
