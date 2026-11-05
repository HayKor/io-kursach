package ru.haykor.sudv.server.records.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ElementsIntoSet
import dev.zacsweers.metro.Provides
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestamp
import ru.haykor.sudv.shared.domain.PenaltyStatus
import ru.haykor.sudv.shared.domain.PenaltyType

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

@BindingContainer
@ContributesTo(AppScope::class)
object RecordsTables {
    @Provides
    @ElementsIntoSet
    fun tables(): Set<Table> = setOf(Departments, Employees, Penalties, PenaltyRemovals, Attachments, StatusEvents)
}
