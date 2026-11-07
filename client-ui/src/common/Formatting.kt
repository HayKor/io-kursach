package ru.haykor.sudv.client.ui.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import ru.haykor.sudv.shared.contract.EmployeeDto
import ru.haykor.sudv.shared.contract.EmployeeShortDto
import kotlin.time.Instant

private val dateFormat = LocalDate.Format {
    day(); char('.'); monthNumber(); char('.'); year()
}

private val dateTimeFormat = LocalDateTime.Format {
    day(); char('.'); monthNumber(); char('.'); year(); char(' '); hour(); char(':'); minute()
}

/** `07.11.2026` */
fun LocalDate.display(): String = format(dateFormat)

/** `07.11.2026 14:05` in [timeZone], the device's own by default. */
fun Instant.display(timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
    toLocalDateTime(timeZone).format(dateTimeFormat)

/** `Иванов Иван Иванович`; the middle name is optional. */
fun fullName(lastName: String, firstName: String, middleName: String?): String =
    listOfNotNull(lastName, firstName, middleName?.takeIf { it.isNotBlank() }).joinToString(" ")

/** `Иванов И. И.` for lists where the full name does not fit. */
fun shortName(lastName: String, firstName: String, middleName: String?): String =
    listOfNotNull(lastName, firstName.initial(), middleName?.initial()).joinToString(" ")

private fun String.initial(): String? = trim().firstOrNull()?.let { "$it." }

val EmployeeDto.fullName: String get() = fullName(lastName, firstName, middleName)
val EmployeeShortDto.fullName: String get() = fullName(lastName, firstName, middleName)
val EmployeeShortDto.shortName: String get() = shortName(lastName, firstName, middleName)

/** `1 день`, `3 дня`, `11 дней`: Russian picks the noun form by the last one or two digits. */
fun days(count: Int): String {
    val form = when {
        count % 100 in 11..14 -> "дней"
        count % 10 == 1 -> "день"
        count % 10 in 2..4 -> "дня"
        else -> "дней"
    }
    return "$count $form"
}
