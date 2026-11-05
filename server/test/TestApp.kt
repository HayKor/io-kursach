package ru.haykor.sudv.server

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.testcontainers.postgresql.PostgreSQLContainer
import ru.haykor.sudv.server.records.data.Departments
import ru.haykor.sudv.server.records.data.Employees
import ru.haykor.sudv.shared.contract.ApiRoutes
import ru.haykor.sudv.shared.contract.CreatePenaltyRequest
import ru.haykor.sudv.shared.contract.CreateUserRequest
import ru.haykor.sudv.shared.contract.DepartmentDto
import ru.haykor.sudv.shared.contract.LoginRequest
import ru.haykor.sudv.shared.contract.LoginResponse
import ru.haykor.sudv.shared.contract.PenaltyDto
import ru.haykor.sudv.shared.domain.DepartmentId
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.PenaltyType
import ru.haykor.sudv.shared.domain.UserRole
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.time.Clock

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
    config: Map<String, String> = emptyMap(),
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
            // Applied after the defaults above, so a test can override any of them, e.g. to turn the scheduler on.
            config.forEach { (key, value) -> put(key, value) }
        }
        beforeStart()
        // The embedded server (and module(), which creates the schema) only starts lazily on the
        // first client call otherwise; tests that only talk to the database directly need it eagerly.
        startApplication()

        block()
    }
}

/** The running application's Metro graph, for tests that need to call a service directly (e.g. [AppGraph.expiryService]). */
fun ApplicationTestBuilder.appGraph(): AppGraph = application.attributes[AppGraphKey]

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

/** Looks up a bootstrapped department (see `application.yaml`'s `bootstrap.departments`) by its exact name. */
suspend fun ApplicationTestBuilder.departmentId(name: String): DepartmentId =
    api().get(ApiRoutes.DEPARTMENTS) { bearer(adminToken()) }.body<List<DepartmentDto>>().single { it.name == name }.id

/** Writes an employee directly to the database: [createUser] needs an employee before any HR token exists. */
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

/**
 * Creates a penalty for [employeeId] through `POST` [ApiRoutes.PENALTIES]. Dates default to a week-long window
 * ending 10 days before today so the call stays valid under [ru.haykor.sudv.shared.domain.Validation.penalty] regardless
 * of when the test suite runs. [hrToken] defaults to a freshly created HR account when not supplied.
 */
suspend fun ApplicationTestBuilder.createPenalty(
    employeeId: EmployeeId,
    type: PenaltyType = PenaltyType.REMARK,
    orderDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()).minus(10, DateTimeUnit.DAY),
    violationDate: LocalDate = orderDate.minus(7, DateTimeUnit.DAY),
    description: String = "Опоздание на рабочее место",
    orderNumber: String = "ORD-${testSequence.incrementAndGet()}",
    hrToken: String? = null,
): PenaltyDto {
    val token = hrToken ?: createUser(UserRole.HR)
    return api().post(ApiRoutes.PENALTIES) {
        bearer(token)
        contentType(ContentType.Application.Json)
        setBody(CreatePenaltyRequest(employeeId, type, violationDate, description, orderNumber, orderDate))
    }.body()
}
