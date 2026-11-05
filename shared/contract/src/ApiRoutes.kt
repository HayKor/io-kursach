package ru.haykor.sudv.contract

import ru.haykor.sudv.domain.AttachmentId
import ru.haykor.sudv.domain.EmployeeId
import ru.haykor.sudv.domain.PenaltyId
import ru.haykor.sudv.domain.ReportId
import ru.haykor.sudv.domain.UserId

/**
 * Constants with `{id}` are route templates for the server, functions build concrete paths for the client.
 * Every route except [LOGIN] requires the `Authorization: Bearer <token>` header.
 */
object ApiRoutes {
    const val ID = "id"

    /** `POST` [LoginRequest] → [LoginResponse]. */
    const val LOGIN = "/api/auth/login"

    /** `GET` → list of [UserDto]; `POST` [CreateUserRequest] → [UserDto]. */
    const val USERS = "/api/users"

    /** `POST` → [UserDto]. */
    const val USER_BLOCK = "/api/users/{id}/block"

    /** `POST` → [UserDto]. */
    const val USER_UNBLOCK = "/api/users/{id}/unblock"

    /** `GET` → list of [DepartmentDto]. */
    const val DEPARTMENTS = "/api/departments"

    /** `GET` ([ApiParams.QUERY], [ApiParams.DEPARTMENT_ID], [ApiParams.INCLUDE_ARCHIVED]) → list of [EmployeeDto]; `POST` [EmployeeRequest] → [EmployeeDto]. */
    const val EMPLOYEES = "/api/employees"

    /** `GET` → [EmployeeDto]; `PUT` [EmployeeRequest] → [EmployeeDto]. */
    const val EMPLOYEE = "/api/employees/{id}"

    /** `POST` → [EmployeeDto]. */
    const val EMPLOYEE_ARCHIVE = "/api/employees/{id}/archive"

    /** `GET` → [EmployeeCardDto]. */
    const val EMPLOYEE_CARD = "/api/employees/{id}/card"

    /** `GET` ([ApiParams.FORMAT]) → card file. */
    const val EMPLOYEE_CARD_EXPORT = "/api/employees/{id}/card/export"

    /** `GET` ([PenaltyFilter]) → list of [PenaltyDto]; `POST` [CreatePenaltyRequest] → [PenaltyDto]. */
    const val PENALTIES = "/api/penalties"

    /** `GET` ([PenaltyFilter], [ApiParams.FORMAT]) → registry file. */
    const val PENALTIES_EXPORT = "/api/penalties/export"

    /** `GET` → [PenaltyDetailsDto]. */
    const val PENALTY = "/api/penalties/{id}"

    /** `POST` [PenaltyRemovalDto] → [PenaltyDto]. */
    const val PENALTY_REMOVAL = "/api/penalties/{id}/removal"

    /** `POST` multipart with part [ApiParams.FILE] → [AttachmentDto]. */
    const val PENALTY_ATTACHMENTS = "/api/penalties/{id}/attachments"

    /** `GET` → attachment file; `DELETE` → 204. */
    const val ATTACHMENT = "/api/attachments/{id}"

    /** `GET` ([ApiParams.PERIOD_FROM], [ApiParams.PERIOD_TO], [ApiParams.DEPARTMENT_ID]) → [SummaryReportDto], not saved to the archive. */
    const val REPORTS_SUMMARY = "/api/reports/summary"

    /** `GET` → list of archived [ReportDto]; `POST` [CreateReportRequest] → [ReportDto]. */
    const val REPORTS = "/api/reports"

    /** `GET` → archived report file. */
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

/** Query parameter and multipart part names. */
object ApiParams {
    const val FORMAT = "format"

    /** Matches full name or personnel number. */
    const val QUERY = "query"

    /** Repeated once per department in [ApiRoutes.REPORTS_SUMMARY]; omitting it there means all departments. */
    const val DEPARTMENT_ID = "departmentId"

    /** Defaults to `false`. */
    const val INCLUDE_ARCHIVED = "includeArchived"

    const val STATUS = "status"
    const val EMPLOYEE_NAME = "employeeName"
    const val ORDER_DATE_FROM = "orderDateFrom"
    const val ORDER_DATE_TO = "orderDateTo"
    const val SORT = "sort"
    const val DIRECTION = "direction"

    const val PERIOD_FROM = "periodFrom"
    const val PERIOD_TO = "periodTo"

    const val FILE = "file"
}
