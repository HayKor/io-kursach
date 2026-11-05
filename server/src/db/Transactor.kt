package ru.haykor.sudv.db

import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/** A nested call joins the enclosing transaction instead of starting a new one. */
class Transactor(private val db: R2dbcDatabase) {
    suspend fun <T> tx(block: suspend () -> T): T = suspendTransaction(db) { block() }
}
