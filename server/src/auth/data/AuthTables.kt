package ru.haykor.sudv.server.auth.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ElementsIntoSet
import dev.zacsweers.metro.Provides
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import ru.haykor.sudv.server.records.data.Employees
import ru.haykor.sudv.shared.domain.UserRole

object Users : LongIdTable("users") {
    val login = varchar("login", 50).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val role = enumerationByName<UserRole>("role", 20)
    val isBlocked = bool("is_blocked").default(false)
    val employeeId = reference("employee_id", Employees).uniqueIndex()
}

@BindingContainer
@ContributesTo(AppScope::class)
object AuthTables {
    @Provides
    @ElementsIntoSet
    fun tables(): Set<Table> = setOf(Users)
}
