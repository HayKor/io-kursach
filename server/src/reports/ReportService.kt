package ru.haykor.sudv.reports

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.todayIn
import ru.haykor.sudv.api.ApiException
import ru.haykor.sudv.auth.CurrentUser
import ru.haykor.sudv.contract.CreateReportRequest
import ru.haykor.sudv.contract.EmployeeCardDto
import ru.haykor.sudv.contract.ErrorCode
import ru.haykor.sudv.contract.PenaltyDto
import ru.haykor.sudv.contract.PenaltyFilter
import ru.haykor.sudv.contract.ReportDto
import ru.haykor.sudv.contract.SummaryReportDto
import ru.haykor.sudv.db.IEmployeeRepository
import ru.haykor.sudv.db.IPenaltyRepository
import ru.haykor.sudv.db.IReportRepository
import ru.haykor.sudv.db.Transactor
import ru.haykor.sudv.domain.DepartmentId
import ru.haykor.sudv.domain.EmployeeId
import ru.haykor.sudv.domain.ReportFormat
import ru.haykor.sudv.domain.ReportId
import ru.haykor.sudv.domain.Validation
import ru.haykor.sudv.domain.displayName
import ru.haykor.sudv.files.IFileStorage
import ru.haykor.sudv.records.IRecordService
import ru.haykor.sudv.records.StoredFile
import kotlin.time.Clock

interface IReportService {
    suspend fun employeeCard(actor: CurrentUser, id: EmployeeId): EmployeeCardDto
    suspend fun exportEmployeeCard(actor: CurrentUser, id: EmployeeId, format: ReportFormat): StoredFile
    suspend fun exportPenalties(actor: CurrentUser, filter: PenaltyFilter, format: ReportFormat): StoredFile

    /** An empty [departmentIds] means all departments; an unknown id is [ErrorCode.VALIDATION_FAILED]. */
    suspend fun summary(from: LocalDate, to: LocalDate, departmentIds: List<DepartmentId>): SummaryReportDto

    /** Computes the summary, renders it to a file and saves it to the archive. */
    suspend fun createReport(request: CreateReportRequest): ReportDto

    /** Newest first. */
    suspend fun listReports(): List<ReportDto>

    /** Serves the file saved by [createReport]; it is never regenerated. */
    suspend fun reportFile(id: ReportId): StoredFile
}

private val REGISTRY_HEADERS =
    listOf("ФИО", "Табельный номер", "Подразделение", "Вид", "Дата нарушения", "Описание", "Номер приказа", "Дата приказа", "Дата погашения", "Статус")
private val SUMMARY_HEADERS = listOf("Подразделение", "Замечаний", "Выговоров", "Всего")
private const val TOTAL_ROW_LABEL = "Итого"

private val DATE_FORMAT = LocalDate.Format {
    day()
    chars(".")
    monthNumber()
    chars(".")
    year()
}

private val SAFE_FILE_NAME_CHARS = Regex("[^A-Za-z0-9_-]")

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ReportService(
    private val recordService: IRecordService,
    private val employeeRepository: IEmployeeRepository,
    private val penaltyRepository: IPenaltyRepository,
    private val reportRepository: IReportRepository,
    private val fileStorage: IFileStorage,
    private val transactor: Transactor,
    private val clock: Clock,
    private val timeZone: TimeZone,
) : IReportService {

    override suspend fun employeeCard(actor: CurrentUser, id: EmployeeId): EmployeeCardDto {
        val employee = recordService.getEmployee(actor, id)
        return EmployeeCardDto(employee, penaltyRepository.findByEmployee(id))
    }

    override suspend fun exportEmployeeCard(actor: CurrentUser, id: EmployeeId, format: ReportFormat): StoredFile {
        val card = employeeCard(actor, id)
        val bytes = renderFile(REGISTRY_HEADERS, card.penalties.map { it.toRegistryRow() }, format, "Карточка")
        val fileName = format.cardFileName(card.employee.personnelNumber, clock.todayIn(timeZone))
        return StoredFile(fileName, format.contentType(), bytes)
    }

    override suspend fun exportPenalties(actor: CurrentUser, filter: PenaltyFilter, format: ReportFormat): StoredFile {
        val penalties = recordService.findPenalties(actor, filter)
        val bytes = renderFile(REGISTRY_HEADERS, penalties.map { it.toRegistryRow() }, format, "Реестр")
        val fileName = format.penaltiesFileName(clock.todayIn(timeZone))
        return StoredFile(fileName, format.contentType(), bytes)
    }

    override suspend fun summary(from: LocalDate, to: LocalDate, departmentIds: List<DepartmentId>): SummaryReportDto {
        validatePeriod(from, to)
        val resolvedDepartmentIds = resolveDepartmentIds(departmentIds)
        return SummaryReportDto(from, to, penaltyRepository.countByDepartment(from, to, resolvedDepartmentIds))
    }

    override suspend fun createReport(request: CreateReportRequest): ReportDto {
        validatePeriod(request.periodFrom, request.periodTo)
        val resolvedDepartmentIds = resolveDepartmentIds(request.departmentIds)
        val summaryRows = penaltyRepository.countByDepartment(request.periodFrom, request.periodTo, resolvedDepartmentIds)
        val summary = SummaryReportDto(request.periodFrom, request.periodTo, summaryRows)
        val bytes = renderFile(SUMMARY_HEADERS, summary.toSummaryRows(), request.format, "Сводный отчёт")

        val storageKey = fileStorage.save(request.format.contentType(), bytes, validateAttachment = false)
        return try {
            transactor.tx {
                val id = reportRepository.create(
                    request.periodFrom,
                    request.periodTo,
                    request.format,
                    storageKey,
                    resolvedDepartmentIds,
                    clock.now(),
                )
                reportRepository.findById(id)?.dto ?: error("Report $id disappeared right after creation")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fileStorage.delete(storageKey)
            throw e
        }
    }

    override suspend fun listReports(): List<ReportDto> = reportRepository.findAll()

    override suspend fun reportFile(id: ReportId): StoredFile {
        val record = reportRepository.findById(id) ?: throw ApiException(ErrorCode.NOT_FOUND, "Отчёт не найден")
        val bytes = fileStorage.read(record.storageKey) ?: throw ApiException(ErrorCode.NOT_FOUND, "Файл отчёта не найден")
        val fileName = record.dto.format.summaryFileName(record.dto.periodFrom, record.dto.periodTo)
        return StoredFile(fileName, record.dto.format.contentType(), bytes)
    }

    private fun validatePeriod(from: LocalDate, to: LocalDate) {
        val errors = Validation.period(from, to)
        if (errors.isNotEmpty()) throw ApiException(ErrorCode.VALIDATION_FAILED, errors.joinToString("; "))
    }

    /** An empty request means all departments; any explicit id must exist. */
    private suspend fun resolveDepartmentIds(requested: List<DepartmentId>): List<DepartmentId> {
        if (requested.isEmpty()) return employeeRepository.findAllDepartments().map { it.id }
        // Deduplicated: report_departments has (report_id, department_id) as its primary key.
        val distinct = requested.distinct()
        distinct.forEach { id ->
            employeeRepository.findDepartment(id) ?: throw ApiException(ErrorCode.VALIDATION_FAILED, "Подразделение не найдено")
        }
        return distinct
    }

    private suspend fun renderFile(headers: List<String>, dataRows: List<List<String>>, format: ReportFormat, sheetName: String): ByteArray =
        withContext(Dispatchers.Default) {
            val rows = listOf(headers) + dataRows
            when (format) {
                ReportFormat.CSV -> CsvWriter(rows)
                ReportFormat.XLSX -> XlsxWriter(sheetName, rows)
            }
        }
}

private fun PenaltyDto.toRegistryRow(): List<String> = listOf(
    listOfNotNull(employee.lastName, employee.firstName, employee.middleName).joinToString(" "),
    employee.personnelNumber,
    employee.department.name,
    type.displayName,
    violationDate.format(DATE_FORMAT),
    description,
    orderNumber,
    orderDate.format(DATE_FORMAT),
    expiryDate.format(DATE_FORMAT),
    status.displayName,
)

private fun SummaryReportDto.toSummaryRows(): List<List<String>> {
    val dataRows = rows.map { listOf(it.department.name, it.remarkCount.toString(), it.reprimandCount.toString(), it.totalCount.toString()) }
    val totalRemark = rows.sumOf { it.remarkCount }
    val totalReprimand = rows.sumOf { it.reprimandCount }
    val totalRow = listOf(TOTAL_ROW_LABEL, totalRemark.toString(), totalReprimand.toString(), (totalRemark + totalReprimand).toString())
    return dataRows + listOf(totalRow)
}

private fun ReportFormat.extension(): String = when (this) {
    ReportFormat.XLSX -> "xlsx"
    ReportFormat.CSV -> "csv"
}

private fun ReportFormat.contentType(): String = when (this) {
    ReportFormat.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    ReportFormat.CSV -> "text/csv; charset=utf-8"
}

private fun ReportFormat.penaltiesFileName(today: LocalDate) = "penalties_$today.${extension()}"

private fun ReportFormat.cardFileName(personnelNumber: String, today: LocalDate) =
    "card_${SAFE_FILE_NAME_CHARS.replace(personnelNumber, "_")}_$today.${extension()}"

private fun ReportFormat.summaryFileName(from: LocalDate, to: LocalDate) = "summary_${from}_$to.${extension()}"
