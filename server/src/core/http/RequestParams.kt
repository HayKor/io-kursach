package ru.haykor.sudv.server.core.http

import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import kotlinx.datetime.LocalDate
import ru.haykor.sudv.server.core.error.ApiException
import ru.haykor.sudv.shared.contract.ApiRoutes
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.domain.DepartmentId

/** Parses the `{id}` path parameter; a missing or non-numeric value is a client error. */
fun ApplicationCall.pathIdValue(): Long =
    parameters[ApiRoutes.ID]?.toLongOrNull() ?: throw ApiException(ErrorCode.VALIDATION_FAILED, "Некорректный идентификатор")

/** A missing [name] returns `null`; a present but non-numeric value is [ErrorCode.VALIDATION_FAILED]. */
fun Parameters.departmentIdOrNull(name: String): DepartmentId? = this[name]?.let { raw ->
    raw.toLongOrNull()?.let { DepartmentId(it) }
        ?: throw ApiException(ErrorCode.VALIDATION_FAILED, "Некорректный идентификатор подразделения")
}

/** Empty when [name] is absent; a non-numeric value in any repetition is [ErrorCode.VALIDATION_FAILED]. */
fun Parameters.departmentIds(name: String): List<DepartmentId> = getAll(name)?.map { raw ->
    raw.toLongOrNull()?.let { DepartmentId(it) }
        ?: throw ApiException(ErrorCode.VALIDATION_FAILED, "Некорректный идентификатор подразделения")
} ?: emptyList()

/** A missing [name] returns `null`; a present but unparsable value is [ErrorCode.VALIDATION_FAILED]. */
fun Parameters.localDateOrNull(name: String): LocalDate? = this[name]?.let { raw ->
    runCatching { LocalDate.parse(raw) }.getOrElse { throw ApiException(ErrorCode.VALIDATION_FAILED, "Некорректная дата") }
}

/** A missing [name] returns `null`; a present but unknown enum value throws [ApiException] with [message]. */
inline fun <reified T : Enum<T>> Parameters.enumOrNull(name: String, message: String): T? = this[name]?.let { raw ->
    enumValues<T>().firstOrNull { it.name == raw } ?: throw ApiException(ErrorCode.VALIDATION_FAILED, message)
}
