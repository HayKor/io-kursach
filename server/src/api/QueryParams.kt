package ru.haykor.sudv.api

import io.ktor.http.Parameters
import kotlinx.datetime.LocalDate
import ru.haykor.sudv.contract.ErrorCode
import ru.haykor.sudv.domain.DepartmentId

/** A missing [name] returns `null`; a present but non-numeric value is [ErrorCode.VALIDATION_FAILED]. */
fun Parameters.departmentIdOrNull(name: String): DepartmentId? = this[name]?.let { raw ->
    raw.toLongOrNull()?.let { DepartmentId(it) }
        ?: throw ApiException(ErrorCode.VALIDATION_FAILED, "Некорректный идентификатор подразделения")
}

/** A missing [name] returns `null`; a present but unparsable value is [ErrorCode.VALIDATION_FAILED]. */
fun Parameters.localDateOrNull(name: String): LocalDate? = this[name]?.let { raw ->
    runCatching { LocalDate.parse(raw) }.getOrElse { throw ApiException(ErrorCode.VALIDATION_FAILED, "Некорректная дата") }
}

/** A missing [name] returns `null`; a present but unknown enum value throws [ApiException] with [message]. */
inline fun <reified T : Enum<T>> Parameters.enumOrNull(name: String, message: String): T? = this[name]?.let { raw ->
    enumValues<T>().firstOrNull { it.name == raw } ?: throw ApiException(ErrorCode.VALIDATION_FAILED, message)
}
