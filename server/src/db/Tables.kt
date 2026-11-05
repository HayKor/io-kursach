package ru.haykor.sudv.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import ru.haykor.sudv.domain.PenaltyStatus
import ru.haykor.sudv.domain.PenaltyType
import ru.haykor.sudv.domain.ReportFormat
import ru.haykor.sudv.domain.UserRole

object Departments : LongIdTable("departments") {
    val name = varchar("name", 200).uniqueIndex()
}

object Employees : LongIdTable("employees") {
    val personnelNumber = varchar("personnel_number", 20).uniqueIndex()
    val lastName = varchar("last_name", 100)
    val firstName = varchar("first_name", 100)
    val middleName = varchar("middle_name", 100).nullable()
    val position = varchar("position", 200)
    val departmentId = reference("department_id", Departments)
    val isArchived = bool("is_archived").default(false)
}

object Users : LongIdTable("users") {
    val login = varchar("login", 50).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val role = enumerationByName<UserRole>("role", 20)
    val isBlocked = bool("is_blocked").default(false)
    val employeeId = reference("employee_id", Employees).uniqueIndex()
}

object Penalties : LongIdTable("penalties") {
    val employeeId = reference("employee_id", Employees)
    val type = enumerationByName<PenaltyType>("type", 20)
    val violationDate = date("violation_date")
    val description = text("description")
    val orderNumber = varchar("order_number", 50)
    val orderDate = date("order_date")
    val status = enumerationByName<PenaltyStatus>("status", 20)

    init {
        check("order_date_after_violation_date") { orderDate greaterEq violationDate }
    }
}

object PenaltyRemovals : Table("penalty_removals") {
    val penaltyId = reference("penalty_id", Penalties)
    val orderNumber = varchar("order_number", 50)
    val orderDate = date("order_date")

    override val primaryKey = PrimaryKey(penaltyId)
}

object Attachments : LongIdTable("attachments") {
    val penaltyId = reference("penalty_id", Penalties)
    val fileName = varchar("file_name", 255)
    val contentType = varchar("content_type", 50)
    val sizeBytes = integer("size_bytes")
    val storageKey = varchar("storage_key", 255)
    val uploadedAt = timestamp("uploaded_at")

    init {
        check("attachments_size_bytes_positive") { sizeBytes greater 0 }
    }
}

object StatusEvents : LongIdTable("status_events") {
    val penaltyId = reference("penalty_id", Penalties)
    val status = enumerationByName<PenaltyStatus>("status", 20)
    val changedAt = timestamp("changed_at")
}

object Reports : LongIdTable("reports") {
    val periodFrom = date("period_from")
    val periodTo = date("period_to")
    val format = enumerationByName<ReportFormat>("format", 10)
    val storageKey = varchar("storage_key", 255)
    val createdAt = timestamp("created_at")

    init {
        check("reports_period_valid") { periodTo greaterEq periodFrom }
    }
}

object ReportDepartments : Table("report_departments") {
    val reportId = reference("report_id", Reports)
    val departmentId = reference("department_id", Departments)

    override val primaryKey = PrimaryKey(reportId, departmentId)
}

private val allTables = arrayOf(
    Departments,
    Employees,
    Users,
    Penalties,
    PenaltyRemovals,
    Attachments,
    StatusEvents,
    Reports,
    ReportDepartments,
)

suspend fun createSchema(db: R2dbcDatabase) {
    suspendTransaction(db) {
        SchemaUtils.create(*allTables)
    }
}
