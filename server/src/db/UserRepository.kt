package ru.haykor.sudv.db

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import ru.haykor.sudv.contract.DepartmentDto
import ru.haykor.sudv.contract.EmployeeShortDto
import ru.haykor.sudv.contract.UserDto
import ru.haykor.sudv.domain.DepartmentId
import ru.haykor.sudv.domain.EmployeeId
import ru.haykor.sudv.domain.UserId
import ru.haykor.sudv.domain.UserRole

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

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class UserRepository(private val transactor: Transactor) : IUserRepository {

    override suspend fun findCredentials(login: String): UserCredentials? = transactor.tx {
        (Users innerJoin Employees)
            .select(Users.id, Users.passwordHash, Users.isBlocked, Employees.isArchived)
            .where { Users.login eq login }
            .singleOrNull()
            ?.let {
                UserCredentials(
                    id = UserId(it[Users.id].value),
                    passwordHash = it[Users.passwordHash],
                    isBlocked = it[Users.isBlocked],
                    employeeArchived = it[Employees.isArchived],
                )
            }
    }

    override suspend fun findAuthenticated(id: UserId): AuthenticatedUser? = transactor.tx {
        (Users innerJoin Employees)
            .select(Users.id, Users.role, Users.isBlocked, Employees.departmentId)
            .where { (Users.id eq id.value) and (Employees.isArchived eq false) }
            .singleOrNull()
            ?.let {
                AuthenticatedUser(
                    id = UserId(it[Users.id].value),
                    role = it[Users.role],
                    departmentId = DepartmentId(it[Employees.departmentId].value),
                    isBlocked = it[Users.isBlocked],
                )
            }
    }

    override suspend fun findById(id: UserId): UserDto? = transactor.tx {
        userQuery().where { Users.id eq id.value }.singleOrNull()?.toUserDto()
    }

    override suspend fun findAll(): List<UserDto> = transactor.tx {
        userQuery().orderBy(Users.login, SortOrder.ASC).toList().map { it.toUserDto() }
    }

    override suspend fun existsByLogin(login: String): Boolean = transactor.tx {
        Users.selectAll().where { Users.login eq login }.toList().isNotEmpty()
    }

    override suspend fun existsByEmployee(employeeId: EmployeeId): Boolean = transactor.tx {
        Users.selectAll().where { Users.employeeId eq employeeId.value }.toList().isNotEmpty()
    }

    override suspend fun existsByRole(role: UserRole): Boolean = transactor.tx {
        Users.selectAll().where { Users.role eq role }.toList().isNotEmpty()
    }

    override suspend fun create(login: String, passwordHash: String, role: UserRole, employeeId: EmployeeId): UserId = transactor.tx {
        val id = Users.insertAndGetId {
            it[Users.login] = login
            it[Users.passwordHash] = passwordHash
            it[Users.role] = role
            it[Users.employeeId] = employeeId.value
        }
        UserId(id.value)
    }

    override suspend fun setBlocked(id: UserId, blocked: Boolean) {
        transactor.tx { Users.update({ Users.id eq id.value }) { it[Users.isBlocked] = blocked } }
    }

    private fun userQuery() = (Users innerJoin Employees innerJoin Departments).selectAll()

    private fun ResultRow.toUserDto() = UserDto(
        id = UserId(this[Users.id].value),
        login = this[Users.login],
        role = this[Users.role],
        isBlocked = this[Users.isBlocked],
        employee = EmployeeShortDto(
            id = EmployeeId(this[Employees.id].value),
            personnelNumber = this[Employees.personnelNumber],
            lastName = this[Employees.lastName],
            firstName = this[Employees.firstName],
            middleName = this[Employees.middleName],
            department = DepartmentDto(DepartmentId(this[Departments.id].value), this[Departments.name]),
        ),
    )
}
