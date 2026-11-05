package ru.haykor.sudv

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.TestApplicationBuilder
import io.ktor.server.testing.testApplication
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.testcontainers.postgresql.PostgreSQLContainer
import ru.haykor.sudv.contract.ApiRoutes
import ru.haykor.sudv.contract.CreateUserRequest
import ru.haykor.sudv.contract.LoginRequest
import ru.haykor.sudv.contract.LoginResponse
import ru.haykor.sudv.db.Departments
import ru.haykor.sudv.db.Employees
import ru.haykor.sudv.domain.EmployeeId
import ru.haykor.sudv.domain.UserRole
import java.util.concurrent.atomic.AtomicInteger
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
 * Runs [block] against the test database: `module()` creates the schema and runs bootstrap itself on
 * startup, exactly as it does against the real database. [beforeStart] runs before startup, for tests
 * that need to register an extra test-only route. [wipe] is `false` to start a second application
 * against the data a previous [apiTest] call already left behind, e.g. to check bootstrap idempotency.
 */
fun apiTest(
    wipe: Boolean = true,
    beforeStart: TestApplicationBuilder.() -> Unit = {},
    block: suspend ApplicationTestBuilder.() -> Unit,
) = run {
    if (wipe) runBlocking { wipeDatabase() }
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

/** Default admin credentials, matching `application.yaml`; [apiTest] does not override `ADMIN_LOGIN`/`ADMIN_PASSWORD`. */
private const val DEFAULT_ADMIN_LOGIN = "admin"
private const val DEFAULT_ADMIN_PASSWORD = "admin12345"

private val testSequence = AtomicInteger()

fun HttpRequestBuilder.bearer(token: String) {
    header(HttpHeaders.Authorization, "Bearer $token")
}

suspend fun ApplicationTestBuilder.login(login: String, password: String): String {
    val response = api().post(ApiRoutes.LOGIN) {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(login, password))
    }
    return response.body<LoginResponse>().token
}

suspend fun ApplicationTestBuilder.adminToken(): String = login(DEFAULT_ADMIN_LOGIN, DEFAULT_ADMIN_PASSWORD)

/** Writes an employee directly to the database; employee HTTP routes do not exist until phase 4. */
suspend fun ApplicationTestBuilder.createEmployee(
    personnelNumber: String = "EMP-${testSequence.incrementAndGet()}",
    lastName: String = "Иванов",
    firstName: String = "Иван",
    middleName: String? = null,
    position: String = "Инженер",
    departmentName: String = "ИТ-отдел",
): EmployeeId = suspendTransaction(TestDatabase.database) {
    val departmentId = Departments
        .selectAll()
        .where { Departments.name eq departmentName }
        .singleOrNull()
        ?.get(Departments.id)
        ?: Departments.insertAndGetId { it[name] = departmentName }

    val employeeId = Employees.insertAndGetId {
        it[Employees.personnelNumber] = personnelNumber
        it[Employees.lastName] = lastName
        it[Employees.firstName] = firstName
        it[Employees.middleName] = middleName
        it[Employees.position] = position
        it[Employees.departmentId] = departmentId
    }
    EmployeeId(employeeId.value)
}

/** Creates an employee and an account for them through the API, logs in and returns the token. */
suspend fun ApplicationTestBuilder.createUser(role: UserRole, departmentName: String = "ИТ-отдел"): String {
    val seq = testSequence.incrementAndGet()
    val employeeId = createEmployee(personnelNumber = "EMP-$seq", departmentName = departmentName)
    val loginName = "user-$seq"
    val password = "password123"

    api().post(ApiRoutes.USERS) {
        bearer(adminToken())
        contentType(ContentType.Application.Json)
        setBody(CreateUserRequest(loginName, password, role, employeeId))
    }

    return login(loginName, password)
}
