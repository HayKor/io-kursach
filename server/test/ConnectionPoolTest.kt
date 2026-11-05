package ru.haykor.sudv

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import kotlin.test.Test
import kotlin.test.assertEquals

private object PgStatActivity : Table("pg_stat_activity") {
    val datname = varchar("datname", 255).nullable()
}

private fun activeConnectionCount(): Long = runBlocking {
    suspendTransaction(TestDatabase.database) {
        PgStatActivity.selectAll()
            .where { PgStatActivity.datname eq TestDatabase.container.databaseName }
            .toList()
            .size
            .toLong()
    }
}

// Disposal of a pool from an earlier test (or of the one under test here) is asynchronous, so a
// single reading can catch it mid-flight. Poll until two consecutive reads agree, both for the
// "baseline" (previous test's pool may still be closing) and for the "after" count.
private fun stableConnectionCount(timeoutMs: Long = 5_000): Long {
    val deadline = System.currentTimeMillis() + timeoutMs
    var previous = activeConnectionCount()
    while (System.currentTimeMillis() < deadline) {
        Thread.sleep(200)
        val current = activeConnectionCount()
        if (current == previous) return current
        previous = current
    }
    return previous
}

class ConnectionPoolTest {
    @Test
    fun appConnectionPoolIsDisposedAfterApplicationStops() {
        val baseline = stableConnectionCount()

        apiTest {
            // Force the app to actually acquire a connection from its pool.
            val response = client.get("/health")
            assertEquals(HttpStatusCode.OK, response.status)
        }

        val after = stableConnectionCount()

        assertEquals(baseline, after, "the application's connection pool should be disposed after it stops")
    }

    @Test
    fun fifteenSequentialApiTestsDoNotExhaustConnections() {
        repeat(15) {
            apiTest {
                val response = client.get("/health")
                assertEquals(HttpStatusCode.OK, response.status)
            }
        }
    }
}
