package ru.haykor.sudv.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import ru.haykor.sudv.server.auth.data.Users
import ru.haykor.sudv.server.records.data.Departments
import ru.haykor.sudv.server.records.data.Employees
import ru.haykor.sudv.shared.contract.ApiRoutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val configuredDepartments = setOf(
    "Дирекция", "Отдел кадров", "Юридический отдел", "ИТ-отдел", "Бухгалтерия", "Производственные подразделения",
)

class BootstrapTest {
    @Test
    fun createsConfiguredDepartmentsAndAdminAccount() = apiTest {
        val token = adminToken()
        val response = client.get(ApiRoutes.DEPARTMENTS) { header("Authorization", "Bearer $token") }
        assertEquals(HttpStatusCode.OK, response.status)

        val departmentNames = suspendTransaction(TestDatabase.database) {
            Departments.selectAll().toList().map { it[Departments.name] }.toSet()
        }
        assertTrue(departmentNames.containsAll(configuredDepartments), "Missing departments: ${configuredDepartments - departmentNames}")
        assertTrue(response.bodyAsText().isNotBlank())
    }

    @Test
    fun restartingOnExistingDataDoesNotDuplicateDepartmentsOrAdmin() {
        apiTest {
            adminToken()
        }

        apiTest(wipe = false) {
            adminToken()

            val departmentCount = suspendTransaction(TestDatabase.database) { Departments.selectAll().toList().size }
            assertEquals(configuredDepartments.size, departmentCount, "departments must not be duplicated by a second bootstrap run")

            val employeeCount = suspendTransaction(TestDatabase.database) { Employees.selectAll().toList().size }
            assertEquals(1, employeeCount, "the admin employee must not be duplicated by a second bootstrap run")

            val userCount = suspendTransaction(TestDatabase.database) { Users.selectAll().toList().size }
            assertEquals(1, userCount, "the admin account must not be duplicated by a second bootstrap run")
        }
    }
}
