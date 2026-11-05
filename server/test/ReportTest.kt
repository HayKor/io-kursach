package ru.haykor.sudv

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import ru.haykor.sudv.contract.ApiParams
import ru.haykor.sudv.contract.ApiRoutes
import ru.haykor.sudv.contract.CreateReportRequest
import ru.haykor.sudv.contract.DepartmentDto
import ru.haykor.sudv.contract.EmployeeCardDto
import ru.haykor.sudv.contract.ErrorCode
import ru.haykor.sudv.contract.ErrorResponse
import ru.haykor.sudv.contract.ReportDto
import ru.haykor.sudv.contract.SummaryReportDto
import ru.haykor.sudv.domain.DepartmentId
import ru.haykor.sudv.domain.EmployeeId
import ru.haykor.sudv.domain.PenaltyType
import ru.haykor.sudv.domain.ReportFormat
import ru.haykor.sudv.domain.UserRole
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

private val REGISTRY_HEADER =
    listOf("ФИО", "Табельный номер", "Подразделение", "Вид", "Дата нарушения", "Описание", "Номер приказа", "Дата приказа", "Дата погашения", "Статус")

private fun LocalDate.formatRu(): String = "%02d.%02d.%04d".format(day, month.number, year)

/** Drops the BOM and splits by CRLF; test fixtures below never need `;`/`"` quoting, which [CsvWriterTest] already covers. */
private fun decodeCsv(bytes: ByteArray): List<List<String>> =
    bytes.drop(3).toByteArray().decodeToString().split("\r\n").filter { it.isNotEmpty() }.map { it.split(";") }

/**
 * fastexcel (via its `opczip` dependency) writes a non-standard data descriptor that
 * [java.util.zip.ZipInputStream] misreads as a size mismatch, even though the archive is a valid ZIP;
 * [ZipFile] reads the central directory instead of relying on local per-entry data descriptors.
 */
private fun isValidXlsx(bytes: ByteArray): Boolean {
    val tempFile = Files.createTempFile("sudv-report", ".xlsx")
    return try {
        Files.write(tempFile, bytes)
        ZipFile(tempFile.toFile()).use { it.getEntry("xl/workbook.xml") != null }
    } finally {
        Files.deleteIfExists(tempFile)
    }
}

private suspend fun ApplicationTestBuilder.getCard(token: String, id: EmployeeId): HttpResponse =
    api().get(ApiRoutes.employeeCard(id)) { bearer(token) }

private suspend fun ApplicationTestBuilder.exportCard(token: String, id: EmployeeId, format: ReportFormat): HttpResponse =
    api().get(ApiRoutes.employeeCardExport(id)) {
        bearer(token)
        parameter(ApiParams.FORMAT, format.name)
    }

private suspend fun ApplicationTestBuilder.exportPenalties(token: String, params: Map<String, String>): HttpResponse =
    api().get(ApiRoutes.PENALTIES_EXPORT) {
        bearer(token)
        params.forEach { (name, value) -> parameter(name, value) }
    }

private suspend fun ApplicationTestBuilder.getSummary(
    token: String,
    from: LocalDate,
    to: LocalDate,
    departmentIds: List<DepartmentId> = emptyList(),
): HttpResponse = api().get(ApiRoutes.REPORTS_SUMMARY) {
    bearer(token)
    parameter(ApiParams.PERIOD_FROM, from.toString())
    parameter(ApiParams.PERIOD_TO, to.toString())
    departmentIds.forEach { parameter(ApiParams.DEPARTMENT_ID, it.value.toString()) }
}

private suspend fun ApplicationTestBuilder.postReport(
    token: String,
    from: LocalDate,
    to: LocalDate,
    departmentIds: List<DepartmentId> = emptyList(),
    format: ReportFormat = ReportFormat.CSV,
): HttpResponse = api().post(ApiRoutes.REPORTS) {
    bearer(token)
    contentType(ContentType.Application.Json)
    setBody(CreateReportRequest(from, to, departmentIds, format))
}

class ReportTest {
    @Test
    fun employeeCardSortsPenaltiesByViolationDateRegardlessOfCreationOrder() = apiTest {
        val hr = createUser(UserRole.HR)
        val employeeId = createEmployee()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        // Created in the opposite order of their violation dates.
        val recent = createPenalty(employeeId, orderDate = today.minus(5, DateTimeUnit.DAY), violationDate = today.minus(6, DateTimeUnit.DAY), hrToken = hr)
        val old = createPenalty(employeeId, orderDate = today.minus(15, DateTimeUnit.DAY), violationDate = today.minus(20, DateTimeUnit.DAY), hrToken = hr)

        val card = getCard(hr, employeeId).body<EmployeeCardDto>()
        assertEquals(listOf(old.id, recent.id), card.penalties.map { it.id })
    }

    @Test
    fun csvExportOfCardAndRegistryMatchColumnsAndData() = apiTest {
        val hr = createUser(UserRole.HR)
        val employeeId = createEmployee(
            personnelNumber = "CSV-1",
            lastName = "Петров",
            firstName = "Пётр",
            middleName = "Петрович",
            departmentName = "ИТ-отдел",
        )
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val orderDate = today.minus(30, DateTimeUnit.DAY)
        val violationDate = orderDate.minus(2, DateTimeUnit.DAY)
        createPenalty(
            employeeId,
            type = PenaltyType.REPRIMAND,
            orderDate = orderDate,
            violationDate = violationDate,
            description = "Нарушение трудовой дисциплины",
            orderNumber = "ORD-CSV-1",
            hrToken = hr,
        )

        val expectedRow = listOf(
            "Петров Пётр Петрович",
            "CSV-1",
            "ИТ-отдел",
            "Выговор",
            violationDate.formatRu(),
            "Нарушение трудовой дисциплины",
            "ORD-CSV-1",
            orderDate.formatRu(),
            orderDate.plus(1, DateTimeUnit.YEAR).formatRu(),
            "Действует",
        )

        val cardCsv = exportCard(hr, employeeId, ReportFormat.CSV)
        assertEquals(HttpStatusCode.OK, cardCsv.status)
        assertTrue(cardCsv.contentType().toString().startsWith("text/csv"))
        val cardRows = decodeCsv(cardCsv.bodyAsBytes())
        assertEquals(REGISTRY_HEADER, cardRows[0])
        assertEquals(expectedRow, cardRows[1])

        val registryCsv = exportPenalties(hr, mapOf(ApiParams.FORMAT to ReportFormat.CSV.name, ApiParams.EMPLOYEE_NAME to "Петров"))
        assertEquals(HttpStatusCode.OK, registryCsv.status)
        val registryRows = decodeCsv(registryCsv.bodyAsBytes())
        assertEquals(REGISTRY_HEADER, registryRows[0])
        assertTrue(registryRows.drop(1).any { it == expectedRow })
    }

    @Test
    fun xlsxExportIsAValidZipWithTheRightContentTypeAndFileName() = apiTest {
        val hr = createUser(UserRole.HR)
        val employeeId = createEmployee(personnelNumber = "XLSX-1")
        createPenalty(employeeId, hrToken = hr)

        val response = exportCard(hr, employeeId, ReportFormat.XLSX)
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType().toString().startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        assertTrue(isValidXlsx(response.bodyAsBytes()))

        val disposition = response.headers[HttpHeaders.ContentDisposition] ?: error("Missing Content-Disposition header")
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        assertTrue(disposition.contains("card_XLSX-1_$today.xlsx"))
    }

    @Test
    fun summaryCountsPenaltiesInThePeriodAndGivesZerosOtherwise() = apiTest {
        val hr = createUser(UserRole.HR)
        val itDept = departmentId("ИТ-отдел")
        val accountingDept = departmentId("Бухгалтерия")
        val itEmployee = createEmployee(personnelNumber = "SUM-1", departmentName = "ИТ-отдел")

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val from = today.minus(30, DateTimeUnit.DAY)
        val to = today.minus(1, DateTimeUnit.DAY)

        createPenalty(itEmployee, type = PenaltyType.REMARK, orderDate = today.minus(10, DateTimeUnit.DAY), violationDate = today.minus(11, DateTimeUnit.DAY), hrToken = hr)
        createPenalty(itEmployee, type = PenaltyType.REPRIMAND, orderDate = today.minus(12, DateTimeUnit.DAY), violationDate = today.minus(13, DateTimeUnit.DAY), hrToken = hr)
        // Violation date before `from`: must not be counted.
        createPenalty(itEmployee, type = PenaltyType.REMARK, orderDate = today.minus(40, DateTimeUnit.DAY), violationDate = today.minus(45, DateTimeUnit.DAY), hrToken = hr)

        val response = getSummary(hr, from, to, listOf(itDept, accountingDept))
        assertEquals(HttpStatusCode.OK, response.status)
        val summary = response.body<SummaryReportDto>()

        val itRow = summary.rows.single { it.department.id == itDept }
        assertEquals(1, itRow.remarkCount)
        assertEquals(1, itRow.reprimandCount)

        val accountingRow = summary.rows.single { it.department.id == accountingDept }
        assertEquals(0, accountingRow.remarkCount)
        assertEquals(0, accountingRow.reprimandCount)
    }

    @Test
    fun summaryWithoutDepartmentIdsReturnsAllBootstrappedDepartments() = apiTest {
        val hr = createUser(UserRole.HR)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val allDepartments = api().get(ApiRoutes.DEPARTMENTS) { bearer(hr) }.body<List<DepartmentDto>>()

        val response = getSummary(hr, today.minus(30, DateTimeUnit.DAY), today)
        assertEquals(HttpStatusCode.OK, response.status)
        val summary = response.body<SummaryReportDto>()
        assertEquals(allDepartments.map { it.id }.toSet(), summary.rows.map { it.department.id }.toSet())
    }

    @Test
    fun summaryWithTwoDepartmentIdsReturnsExactlyThoseTwo() = apiTest {
        val hr = createUser(UserRole.HR)
        val itDept = departmentId("ИТ-отдел")
        val accountingDept = departmentId("Бухгалтерия")
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        val response = getSummary(hr, today.minus(30, DateTimeUnit.DAY), today, listOf(itDept, accountingDept))
        assertEquals(HttpStatusCode.OK, response.status)
        val summary = response.body<SummaryReportDto>()
        assertEquals(setOf(itDept, accountingDept), summary.rows.map { it.department.id }.toSet())
    }

    @Test
    fun summaryWithFromAfterToIsValidationFailed() = apiTest {
        val hr = createUser(UserRole.HR)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        val response = getSummary(hr, today, today.minus(5, DateTimeUnit.DAY))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, response.body<ErrorResponse>().code)
    }

    @Test
    fun summaryWithUnknownDepartmentIsValidationFailed() = apiTest {
        val hr = createUser(UserRole.HR)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        val response = getSummary(hr, today.minus(10, DateTimeUnit.DAY), today, listOf(DepartmentId(999_999_999)))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.VALIDATION_FAILED, response.body<ErrorResponse>().code)
    }

    @Test
    fun creatingAReportArchivesItAndServesIdenticalBytesOnEveryDownload() = apiTest {
        val hr = createUser(UserRole.HR)
        val itDept = departmentId("ИТ-отдел")
        val employeeId = createEmployee(departmentName = "ИТ-отдел")
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        createPenalty(employeeId, orderDate = today.minus(5, DateTimeUnit.DAY), violationDate = today.minus(6, DateTimeUnit.DAY), hrToken = hr)

        val createResponse = postReport(hr, today.minus(30, DateTimeUnit.DAY), today, listOf(itDept), ReportFormat.CSV)
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val created = createResponse.body<ReportDto>()
        assertEquals(setOf(itDept), created.departments.map { it.id }.toSet())

        val listed = api().get(ApiRoutes.REPORTS) { bearer(hr) }.body<List<ReportDto>>()
        assertTrue(listed.any { it.id == created.id })

        val firstDownload = api().get(ApiRoutes.reportFile(created.id)) { bearer(hr) }.bodyAsBytes()
        val secondDownload = api().get(ApiRoutes.reportFile(created.id)) { bearer(hr) }.bodyAsBytes()
        assertEquals(firstDownload.toList(), secondDownload.toList())

        val rows = decodeCsv(firstDownload)
        assertTrue(rows.last()[0].startsWith("Итого"))

        val storageKey = appGraph().reportRepository.findById(created.id)?.storageKey ?: error("Report not found")
        val storedFile = Path.of(appGraph().appConfig.filesDir).resolve(storageKey)
        assertTrue(Files.exists(storedFile))
    }

    @Test
    fun duplicateDepartmentIdsInReportRequestAreStoredOnce() = apiTest {
        val hr = createUser(UserRole.HR)
        val itDept = departmentId("ИТ-отдел")
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        val response = postReport(hr, today.minus(30, DateTimeUnit.DAY), today, listOf(itDept, itDept))
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(listOf(itDept), response.body<ReportDto>().departments.map { it.id })
    }

    @Test
    fun managerSeesOwnEmployeeCardButNotAForeignOne() = apiTest {
        val hr = createUser(UserRole.HR)
        val managerToken = createUser(UserRole.MANAGER, departmentName = "ИТ-отдел")
        val ownEmployee = createEmployee(personnelNumber = "MGR-CARD-OWN", departmentName = "ИТ-отдел")
        val otherEmployee = createEmployee(personnelNumber = "MGR-CARD-OTHER", departmentName = "Бухгалтерия")
        createPenalty(ownEmployee, hrToken = hr)
        createPenalty(otherEmployee, hrToken = hr)

        assertEquals(HttpStatusCode.OK, getCard(managerToken, ownEmployee).status)
        assertEquals(HttpStatusCode.Forbidden, getCard(managerToken, otherEmployee).status)

        assertEquals(HttpStatusCode.OK, exportCard(managerToken, ownEmployee, ReportFormat.CSV).status)
        assertEquals(HttpStatusCode.Forbidden, exportCard(managerToken, otherEmployee, ReportFormat.CSV).status)
    }

    @Test
    fun managerPenaltiesExportContainsOnlyTheirOwnDepartment() = apiTest {
        val hr = createUser(UserRole.HR)
        val managerToken = createUser(UserRole.MANAGER, departmentName = "ИТ-отдел")
        val ownEmployee = createEmployee(personnelNumber = "MGR-EXP-OWN", departmentName = "ИТ-отдел")
        val otherEmployee = createEmployee(personnelNumber = "MGR-EXP-OTHER", departmentName = "Бухгалтерия")
        createPenalty(ownEmployee, hrToken = hr)
        createPenalty(otherEmployee, hrToken = hr)

        val response = exportPenalties(managerToken, mapOf(ApiParams.FORMAT to ReportFormat.CSV.name))
        assertEquals(HttpStatusCode.OK, response.status)
        val rows = decodeCsv(response.bodyAsBytes()).drop(1)
        assertTrue(rows.all { it[1] != "MGR-EXP-OTHER" })
        assertTrue(rows.any { it[1] == "MGR-EXP-OWN" })
    }

    @Test
    fun managerCannotAccessSummaryOrArchiveRoutes() = apiTest {
        val managerToken = createUser(UserRole.MANAGER)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        assertEquals(HttpStatusCode.Forbidden, getSummary(managerToken, today.minus(10, DateTimeUnit.DAY), today).status)
        assertEquals(HttpStatusCode.Forbidden, api().get(ApiRoutes.REPORTS) { bearer(managerToken) }.status)
        assertEquals(HttpStatusCode.Forbidden, postReport(managerToken, today.minus(10, DateTimeUnit.DAY), today).status)
    }
}
