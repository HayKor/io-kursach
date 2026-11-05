package ru.haykor.sudv.server.core.database

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/** [tables] come from each feature's `@ElementsIntoSet` contribution; `SchemaUtils` orders them by foreign keys. */
suspend fun createSchema(db: R2dbcDatabase, tables: Set<Table>) {
    suspendTransaction(db) {
        SchemaUtils.create(*tables.toTypedArray())
    }
}
