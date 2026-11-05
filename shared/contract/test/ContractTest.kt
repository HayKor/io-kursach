package ru.haykor.sudv.shared.contract

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import ru.haykor.sudv.shared.domain.DepartmentId
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.PenaltyId
import ru.haykor.sudv.shared.domain.PenaltyStatus
import ru.haykor.sudv.shared.domain.PenaltyType
import ru.haykor.sudv.shared.domain.ReportFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ContractTest {
    private val department = DepartmentDto(id = DepartmentId(1), name = "Бухгалтерия")
    private val employee = EmployeeShortDto(
        id = EmployeeId(7), personnelNumber = "0042", lastName = "Иванов", firstName = "Иван",
        middleName = null, department = department,
    )
    private val penalty = PenaltyDto(
        id = PenaltyId(3), employee = employee, type = PenaltyType.REPRIMAND,
        violationDate = LocalDate(2026, 3, 10), description = "Опоздание",
        orderNumber = "15-к", orderDate = LocalDate(2026, 3, 12), expiryDate = LocalDate(2027, 3, 12),
        status = PenaltyStatus.ACTIVE, removal = null,
    )

    @Test
    fun penaltyRoundTrip() {
        val json = Json.encodeToString(penalty)
        assertTrue("\"id\":3," in json, json)
        assertTrue("\"orderDate\":\"2026-03-12\"" in json, json)
        assertTrue("\"type\":\"REPRIMAND\"" in json, json)
        assertTrue("\"status\":\"ACTIVE\"" in json, json)
        assertEquals(penalty, Json.decodeFromString<PenaltyDto>(json))
    }

    @Test
    fun instantIsIsoString() {
        val event = StatusEventDto(PenaltyStatus.EXPIRED, Instant.parse("2027-03-12T00:00:05Z"))
        val json = Json.encodeToString(event)
        assertEquals("""{"status":"EXPIRED","changedAt":"2027-03-12T00:00:05Z"}""", json)
        assertEquals(event, Json.decodeFromString<StatusEventDto>(json))
    }

    @Test
    fun totalCountIsNotSerialized() {
        val row = SummaryRowDto(department, remarkCount = 2, reprimandCount = 1)
        assertEquals(3, row.totalCount)
        assertTrue("totalCount" !in Json.encodeToString(row))
    }

    @Test
    fun idListIsArrayOfNumbers() {
        val request = CreateReportRequest(
            periodFrom = LocalDate(2026, 1, 1), periodTo = LocalDate(2026, 6, 30),
            departmentIds = listOf(DepartmentId(1), DepartmentId(4)), format = ReportFormat.XLSX,
        )
        val json = Json.encodeToString(request)
        assertTrue("\"departmentIds\":[1,4]" in json, json)
        assertEquals(request, Json.decodeFromString<CreateReportRequest>(json))
    }

    @Test
    fun errorResponseUsesCodeName() {
        val json = Json.encodeToString(ErrorResponse(ErrorCode.DUPLICATE_LOGIN, "Логин занят"))
        assertEquals("""{"code":"DUPLICATE_LOGIN","message":"Логин занят"}""", json)
    }

    @Test
    fun routesSubstituteId() {
        assertEquals("/api/penalties/5/removal", ApiRoutes.penaltyRemoval(PenaltyId(5)))
        assertEquals("/api/employees/9/card/export", ApiRoutes.employeeCardExport(EmployeeId(9)))
    }
}
