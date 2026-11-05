package ru.haykor.sudv

/**
 * Адреса методов API.
 *
 * Константы с `{id}` — шаблоны для объявления маршрутов на сервере,
 * функции с параметром — готовые адреса для запросов клиента.
 * Все методы, кроме [LOGIN], требуют заголовок `Authorization: Bearer <token>`.
 */
object ApiRoutes {
    /** Имя параметра пути с идентификатором. */
    const val ID = "id"

    /** `POST` [LoginRequest] → [LoginResponse]. */
    const val LOGIN = "/api/auth/login"

    /** `GET` → список [UserDto]; `POST` [CreateUserRequest] → [UserDto]. */
    const val USERS = "/api/users"

    /** `POST` → [UserDto]. */
    const val USER_BLOCK = "/api/users/{id}/block"

    /** `POST` → [UserDto]. */
    const val USER_UNBLOCK = "/api/users/{id}/unblock"

    /** `GET` → список [DepartmentDto]. */
    const val DEPARTMENTS = "/api/departments"

    /** `GET` (параметры [ApiParams.QUERY], [ApiParams.DEPARTMENT_ID], [ApiParams.INCLUDE_ARCHIVED]) → список [EmployeeDto]; `POST` [EmployeeRequest] → [EmployeeDto]. */
    const val EMPLOYEES = "/api/employees"

    /** `GET` → [EmployeeDto]; `PUT` [EmployeeRequest] → [EmployeeDto]. */
    const val EMPLOYEE = "/api/employees/{id}"

    /** `POST` → [EmployeeDto]. */
    const val EMPLOYEE_ARCHIVE = "/api/employees/{id}/archive"

    /** `GET` → [EmployeeCardDto] — персональная карточка нарушений. */
    const val EMPLOYEE_CARD = "/api/employees/{id}/card"

    /** `GET` (параметр [ApiParams.FORMAT]) → файл карточки. */
    const val EMPLOYEE_CARD_EXPORT = "/api/employees/{id}/card/export"

    /** `GET` (параметры [PenaltyFilter]) → список [PenaltyDto]; `POST` [CreatePenaltyRequest] → [PenaltyDto]. */
    const val PENALTIES = "/api/penalties"

    /** `GET` (параметры [PenaltyFilter] и [ApiParams.FORMAT]) → файл реестра. */
    const val PENALTIES_EXPORT = "/api/penalties/export"

    /** `GET` → [PenaltyDetailsDto]. */
    const val PENALTY = "/api/penalties/{id}"

    /** `POST` [PenaltyRemovalDto] → [PenaltyDto] — досрочное снятие. */
    const val PENALTY_REMOVAL = "/api/penalties/{id}/removal"

    /** `POST` multipart с частью [ApiParams.FILE] → [AttachmentDto]. */
    const val PENALTY_ATTACHMENTS = "/api/penalties/{id}/attachments"

    /** `GET` → файл вложения; `DELETE` → 204. */
    const val ATTACHMENT = "/api/attachments/{id}"

    /** `GET` (параметры [ApiParams.PERIOD_FROM], [ApiParams.PERIOD_TO], [ApiParams.DEPARTMENT_ID]) → [SummaryReportDto] — предпросмотр без сохранения. */
    const val REPORTS_SUMMARY = "/api/reports/summary"

    /** `GET` → список [ReportDto] (архив); `POST` [CreateReportRequest] → [ReportDto]. */
    const val REPORTS = "/api/reports"

    /** `GET` → файл отчёта из архива. */
    const val REPORT_FILE = "/api/reports/{id}/file"

    fun userBlock(id: UserId) = USER_BLOCK.withId(id.value)
    fun userUnblock(id: UserId) = USER_UNBLOCK.withId(id.value)
    fun employee(id: EmployeeId) = EMPLOYEE.withId(id.value)
    fun employeeArchive(id: EmployeeId) = EMPLOYEE_ARCHIVE.withId(id.value)
    fun employeeCard(id: EmployeeId) = EMPLOYEE_CARD.withId(id.value)
    fun employeeCardExport(id: EmployeeId) = EMPLOYEE_CARD_EXPORT.withId(id.value)
    fun penalty(id: PenaltyId) = PENALTY.withId(id.value)
    fun penaltyRemoval(id: PenaltyId) = PENALTY_REMOVAL.withId(id.value)
    fun penaltyAttachments(id: PenaltyId) = PENALTY_ATTACHMENTS.withId(id.value)
    fun attachment(id: AttachmentId) = ATTACHMENT.withId(id.value)
    fun reportFile(id: ReportId) = REPORT_FILE.withId(id.value)

    private fun String.withId(id: Long) = replace("{$ID}", id.toString())
}

/** Имена query-параметров и частей multipart-запросов. */
object ApiParams {
    /** Формат экспорта, значение [ReportFormat]. */
    const val FORMAT = "format"

    /** Поиск сотрудника по ФИО или табельному номеру. */
    const val QUERY = "query"

    /** Подразделение. В [ApiRoutes.REPORTS_SUMMARY] повторяется для каждого выбранного подразделения. */
    const val DEPARTMENT_ID = "departmentId"

    /** Показывать архивных сотрудников, по умолчанию `false`. */
    const val INCLUDE_ARCHIVED = "includeArchived"

    const val STATUS = "status"
    const val EMPLOYEE_NAME = "employeeName"
    const val ORDER_DATE_FROM = "orderDateFrom"
    const val ORDER_DATE_TO = "orderDateTo"
    const val SORT = "sort"
    const val DIRECTION = "direction"

    const val PERIOD_FROM = "periodFrom"
    const val PERIOD_TO = "periodTo"

    /** Часть multipart-запроса с файлом вложения. */
    const val FILE = "file"
}
