package ru.haykor.sudv

import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import ru.haykor.sudv.db.Departments
import ru.haykor.sudv.db.Employees
import ru.haykor.sudv.db.Penalties
import ru.haykor.sudv.domain.PenaltyStatus
import ru.haykor.sudv.domain.PenaltyType
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertTrue

private object InformationSchemaTables : Table("information_schema.tables") {
    val tableSchema = varchar("table_schema", 64)
    val tableNameColumn = varchar("table_name", 64)
}

class SchemaTest {
    @Test
    fun startupCreatesAllTables() = apiTest {
        // module() already created the schema on startup (see createSchema); just confirm it is there.
        val tableNames = suspendTransaction(TestDatabase.database) {
            InformationSchemaTables
                .selectAll()
                .where { InformationSchemaTables.tableSchema eq "public" }
                .toList()
                .map { it[InformationSchemaTables.tableNameColumn] }
                .toSet()
        }

        val expected = setOf(
            "departments", "employees", "users", "penalties", "penalty_removals",
            "attachments", "status_events", "reports", "report_departments",
        )
        assertTrue(tableNames.containsAll(expected), "Missing tables: ${expected - tableNames}, found: $tableNames")
    }

    @Test
    fun orderDateBeforeViolationDateViolatesCheckConstraint() = apiTest {
        val db = TestDatabase.database

        assertFails {
            suspendTransaction(db) {
                val deptId = Departments.insertAndGetId { it[name] = "Check constraint test" }
                val empId = Employees.insertAndGetId {
                    it[personnelNumber] = "CHK-1"
                    it[lastName] = "Testov"
                    it[firstName] = "Test"
                    it[position] = "Tester"
                    it[departmentId] = deptId
                }
                Penalties.insert {
                    it[employeeId] = empId
                    it[type] = PenaltyType.REMARK
                    it[violationDate] = LocalDate(2026, 1, 10)
                    it[description] = "Invalid order"
                    it[orderNumber] = "1"
                    // order_date before violation_date must violate the check constraint
                    it[orderDate] = LocalDate(2026, 1, 1)
                    it[status] = PenaltyStatus.ACTIVE
                }
            }
        }
    }
}
