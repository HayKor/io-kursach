package ru.haykor.sudv.config

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ru.haykor.sudv.auth.PasswordHasher
import ru.haykor.sudv.contract.EmployeeRequest
import ru.haykor.sudv.db.IEmployeeRepository
import ru.haykor.sudv.db.IUserRepository
import ru.haykor.sudv.db.Transactor
import ru.haykor.sudv.domain.UserRole

private const val ADMIN_PERSONNEL_NUMBER = "ADMIN"

/** Creates the initial departments and the first ADMIN account. Safe to run on every startup. */
@Inject
@SingleIn(AppScope::class)
class Bootstrap(
    private val appConfig: AppConfig,
    private val employeeRepository: IEmployeeRepository,
    private val userRepository: IUserRepository,
    private val transactor: Transactor,
) {
    suspend fun run() {
        transactor.tx {
            appConfig.bootstrapDepartments.forEach { name ->
                if (employeeRepository.findDepartmentByName(name) == null) {
                    employeeRepository.createDepartment(name)
                }
            }

            if (!userRepository.existsByRole(UserRole.ADMIN)) {
                createAdmin()
            }
        }
    }

    private suspend fun createAdmin() {
        val department = employeeRepository.findDepartmentByName(appConfig.adminDepartment)
            ?: error("Bootstrap department '${appConfig.adminDepartment}' is missing; check bootstrap.departments and bootstrap.admin.department")

        val employeeId = employeeRepository.findByPersonnelNumber(ADMIN_PERSONNEL_NUMBER)?.id
            ?: employeeRepository.create(
                EmployeeRequest(
                    personnelNumber = ADMIN_PERSONNEL_NUMBER,
                    lastName = "Администратор",
                    firstName = "СУДВ",
                    position = "Администратор СУДВ",
                    departmentId = department.id,
                ),
            )

        val passwordHash = PasswordHasher.hash(appConfig.adminPassword, appConfig.passwordHashIterations)
        userRepository.create(appConfig.adminLogin, passwordHash, UserRole.ADMIN, employeeId)
    }
}
