package ru.haykor.sudv.client.ui.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class FormattingTest {
    @Test
    fun dateIsDayMonthYearWithLeadingZeros() {
        assertEquals("07.03.2026", LocalDate(2026, 3, 7).display())
    }

    @Test
    fun instantIsShownInTheGivenTimeZone() {
        val instant = Instant.parse("2026-11-06T21:05:00Z")
        assertEquals("07.11.2026 00:05", instant.display(TimeZone.of("Europe/Moscow")))
    }

    @Test
    fun namesWithAndWithoutMiddleName() {
        assertEquals("Иванов Иван Иванович", fullName("Иванов", "Иван", "Иванович"))
        assertEquals("Иванов Иван", fullName("Иванов", "Иван", null))
        assertEquals("Иванов Иван", fullName("Иванов", "Иван", " "))
        assertEquals("Иванов И. И.", shortName("Иванов", "Иван", "Иванович"))
        assertEquals("Иванов И.", shortName("Иванов", "Иван", null))
    }

    @Test
    fun daysAgreeWithTheNumber() {
        assertEquals(
            listOf("0 дней", "1 день", "2 дня", "5 дней", "11 дней", "14 дней", "21 день", "22 дня", "111 дней", "365 дней"),
            listOf(0, 1, 2, 5, 11, 14, 21, 22, 111, 365).map(::days),
        )
    }

    @Test
    fun fileSizesInRussianUnits() {
        assertEquals(
            listOf("0 Б", "1023 Б", "1 КБ", "48 КБ", "1023 КБ", "1,0 МБ", "1,5 МБ", "10,0 МБ"),
            listOf(0L, 1023, 1024, 48 * 1024 + 100, 1024 * 1024 - 1, 1024 * 1024, 1536 * 1024, 10L * 1024 * 1024).map(::fileSize),
        )
    }
}
