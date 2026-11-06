package ru.haykor.sudv.server.auth.domain

import ru.haykor.sudv.shared.contract.UserDto
import ru.haykor.sudv.shared.domain.DepartmentId
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.UserId
import ru.haykor.sudv.shared.domain.UserRole

data class UserCredentials(val id: UserId, val passwordHash: String, val isBlocked: Boolean, val employeeArchived: Boolean)

data class AuthenticatedUser(val id: UserId, val role: UserRole, val departmentId: DepartmentId, val isBlocked: Boolean)

interface IUserRepository {
    suspend fun findCredentials(login: String): UserCredentials?

    /** Returns `null` for an unknown user and for one whose employee is archived; [AuthenticatedUser.isBlocked] still needs checking by the caller. */
    suspend fun findAuthenticated(id: UserId): AuthenticatedUser?
    suspend fun findById(id: UserId): UserDto?

    /** Sorted by login. */
    suspend fun findAll(): List<UserDto>
    suspend fun existsByLogin(login: String): Boolean
    suspend fun existsByEmployee(employeeId: EmployeeId): Boolean
    suspend fun existsByRole(role: UserRole): Boolean
    suspend fun create(login: String, passwordHash: String, role: UserRole, employeeId: EmployeeId): UserId
    suspend fun setBlocked(id: UserId, blocked: Boolean)
}
