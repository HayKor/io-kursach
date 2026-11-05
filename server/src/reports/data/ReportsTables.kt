package ru.haykor.sudv.server.reports.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ElementsIntoSet
import dev.zacsweers.metro.Provides
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestamp
import ru.haykor.sudv.server.records.data.Departments
import ru.haykor.sudv.shared.domain.ReportFormat

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

@BindingContainer
@ContributesTo(AppScope::class)
object ReportsTables {
    @Provides
    @ElementsIntoSet
    fun tables(): Set<Table> = setOf(Reports, ReportDepartments)
}
