package ru.haykor.sudv

import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import ru.haykor.sudv.db.Departments
import ru.haykor.sudv.db.Employees
import ru.haykor.sudv.db.Transactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactorTest {
    @Test
    fun nestedTxJoinsOuterAndRollsBackTogether() = apiTest {
        val db = TestDatabase.database
        val transactor = Transactor(db)

        val deptId = suspendTransaction(db) { Departments.insertAndGetId { it[name] = "Transactor test" } }

        val result = runCatching {
            transactor.tx {
                // Nested call: should join the outer transaction rather than committing on its own.
                transactor.tx {
                    Employees.insert {
                        it[personnelNumber] = "TX-NESTED-1"
                        it[lastName] = "Nested"
                        it[firstName] = "Tx"
                        it[position] = "Tester"
                        it[departmentId] = deptId
                    }
                }
                error("outer transaction failure")
            }
        }
        assertTrue(result.isFailure)

        val count = suspendTransaction(db) {
            Employees.selectAll().where { Employees.personnelNumber eq "TX-NESTED-1" }.toList().size
        }
        assertEquals(0, count, "The row written by the nested tx must be rolled back with the outer one")
    }
}
