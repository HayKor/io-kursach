package ru.haykor.sudv

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.TestApplicationBuilder
import io.ktor.server.testing.testApplication
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.io.path.createTempDirectory

/** A single Postgres container for the whole test run, started lazily on first use. */
object TestDatabase {
    val container: PostgreSQLContainer by lazy {
        PostgreSQLContainer("postgres:16-alpine").apply { start() }
    }

    val r2dbcUrl: String
        get() = "r2dbc:postgresql://${container.host}:${container.getMappedPort(5432)}/${container.databaseName}"

    // Unpooled on purpose: each harness-side query opens and closes its own connection, so there
    // is no second pool to dispose on top of the one each tested application manages itself.
    val database: R2dbcDatabase by lazy {
        R2dbcDatabase.connect {
            setUrl(r2dbcUrl)
            connectionFactoryOptions {
                option(ConnectionFactoryOptions.USER, container.username)
                option(ConnectionFactoryOptions.PASSWORD, container.password)
            }
        }
    }
}

private suspend fun wipeDatabase() {
    suspendTransaction(TestDatabase.database) {
        exec("DROP SCHEMA public CASCADE")
        exec("CREATE SCHEMA public")
    }
}

/**
 * Runs [block] against a freshly wiped database: `module()` creates the schema itself on startup,
 * exactly as it does against the real database. [beforeStart] runs before startup, for tests that
 * need to register an extra test-only route.
 */
fun apiTest(
    beforeStart: TestApplicationBuilder.() -> Unit = {},
    block: suspend ApplicationTestBuilder.() -> Unit,
) = run {
    runBlocking { wipeDatabase() }
    val filesDir = createTempDirectory("sudv-files").toString()

    testApplication {
        configure("application.yaml") {
            put("database.url", TestDatabase.r2dbcUrl)
            put("database.user", TestDatabase.container.username)
            put("database.password", TestDatabase.container.password)
            put("files.dir", filesDir)
            put("security.passwordHashIterations", "1000")
            put("expiry.schedulerEnabled", "false")
        }
        beforeStart()
        // The embedded server (and module(), which creates the schema) only starts lazily on the
        // first client call otherwise; tests that only talk to the database directly need it eagerly.
        startApplication()

        block()
    }
}

/** An HTTP client that (de)serializes JSON bodies; the default `client` does not. */
fun ApplicationTestBuilder.api() = createClient {
    install(ContentNegotiation) { json() }
}
