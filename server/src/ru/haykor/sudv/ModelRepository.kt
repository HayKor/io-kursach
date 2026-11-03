package ru.haykor.sudv

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object ModelTable : IntIdTable("model") {
    val name = varchar("name", 255)
}

@Inject
class ModelRepository(private val database: R2dbcDatabase) {
    suspend fun findAll(): List<Model> = suspendTransaction(db = database) {
        ModelTable.selectAll()
            .map { Model(id = it[ModelTable.id].value, name = it[ModelTable.name]) }
            .toList()
    }
}
