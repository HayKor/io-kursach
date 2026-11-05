package ru.haykor.sudv.shared.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidationTest {
    private val today = LocalDate(2026, 6, 15)


    @Test
    fun employeeValidIsAccepted() {
        assertTrue(Validation.employee("0042", "Иванов", "Иван", "Иванович", "Инженер").isEmpty())
    }

    @Test
    fun employeeValidWithoutMiddleNameIsAccepted() {
        assertTrue(Validation.employee("0042", "Иванов", "Иван", null, "Инженер").isEmpty())
    }

    @Test
    fun employeeBlankPersonnelNumberIsRejected() {
        assertEquals(
            listOf("Табельный номер не должен быть пустым"),
            Validation.employee("   ", "Иванов", "Иван", null, "Инженер"),
        )
    }

    @Test
    fun employeeTooLongPersonnelNumberIsRejected() {
        val errors = Validation.employee("a".repeat(21), "Иванов", "Иван", null, "Инженер")
        assertEquals(listOf("Табельный номер не должен быть длиннее 20 символов"), errors)
    }

    @Test
    fun employeeBlankLastNameIsRejected() {
        val errors = Validation.employee("0042", " ", "Иван", null, "Инженер")
        assertEquals(listOf("Фамилия не должна быть пустой"), errors)
    }

    @Test
    fun employeeTooLongLastNameIsRejected() {
        val errors = Validation.employee("0042", "a".repeat(101), "Иван", null, "Инженер")
        assertEquals(listOf("Фамилия не должна быть длиннее 100 символов"), errors)
    }

    @Test
    fun employeeBlankFirstNameIsRejected() {
        val errors = Validation.employee("0042", "Иванов", " ", null, "Инженер")
        assertEquals(listOf("Имя не должно быть пустым"), errors)
    }

    @Test
    fun employeeTooLongFirstNameIsRejected() {
        val errors = Validation.employee("0042", "Иванов", "a".repeat(101), null, "Инженер")
        assertEquals(listOf("Имя не должно быть длиннее 100 символов"), errors)
    }

    @Test
    fun employeeTooLongMiddleNameIsRejected() {
        val errors = Validation.employee("0042", "Иванов", "Иван", "a".repeat(101), "Инженер")
        assertEquals(listOf("Отчество не должно быть длиннее 100 символов"), errors)
    }

    @Test
    fun employeeBlankPositionIsRejected() {
        val errors = Validation.employee("0042", "Иванов", "Иван", null, " ")
        assertEquals(listOf("Должность не должна быть пустой"), errors)
    }

    @Test
    fun employeeTooLongPositionIsRejected() {
        val errors = Validation.employee("0042", "Иванов", "Иван", null, "a".repeat(201))
        assertEquals(listOf("Должность не должна быть длиннее 200 символов"), errors)
    }


    @Test
    fun penaltyValidIsAccepted() {
        val errors = Validation.penalty(
            violationDate = LocalDate(2026, 6, 1),
            orderDate = LocalDate(2026, 6, 10),
            description = "Опоздание",
            orderNumber = "15-к",
            today = today,
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun penaltyBoundaryDatesAreAccepted() {
        val errors = Validation.penalty(
            violationDate = today,
            orderDate = today,
            description = "Опоздание",
            orderNumber = "15-к",
            today = today,
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun penaltyBlankDescriptionIsRejected() {
        val errors = Validation.penalty(
            violationDate = LocalDate(2026, 6, 1),
            orderDate = LocalDate(2026, 6, 10),
            description = " ",
            orderNumber = "15-к",
            today = today,
        )
        assertEquals(listOf("Описание не должно быть пустым"), errors)
    }

    @Test
    fun penaltyBlankOrderNumberIsRejected() {
        val errors = Validation.penalty(
            violationDate = LocalDate(2026, 6, 1),
            orderDate = LocalDate(2026, 6, 10),
            description = "Опоздание",
            orderNumber = " ",
            today = today,
        )
        assertEquals(listOf("Номер приказа не должен быть пустым"), errors)
    }

    @Test
    fun penaltyTooLongOrderNumberIsRejected() {
        val errors = Validation.penalty(
            violationDate = LocalDate(2026, 6, 1),
            orderDate = LocalDate(2026, 6, 10),
            description = "Опоздание",
            orderNumber = "a".repeat(51),
            today = today,
        )
        assertEquals(listOf("Номер приказа не должен быть длиннее 50 символов"), errors)
    }

    @Test
    fun penaltyViolationAfterOrderDateIsRejected() {
        val errors = Validation.penalty(
            violationDate = LocalDate(2026, 6, 12),
            orderDate = LocalDate(2026, 6, 10),
            description = "Опоздание",
            orderNumber = "15-к",
            today = today,
        )
        assertEquals(listOf("Дата нарушения не может быть позже даты приказа"), errors)
    }

    @Test
    fun penaltyOrderDateInFutureIsRejected() {
        val errors = Validation.penalty(
            violationDate = LocalDate(2026, 6, 1),
            orderDate = LocalDate(2026, 6, 20),
            description = "Опоздание",
            orderNumber = "15-к",
            today = today,
        )
        assertEquals(listOf("Дата приказа не может быть позже текущей даты"), errors)
    }


    @Test
    fun removalValidIsAccepted() {
        val errors = Validation.removal(
            penaltyOrderDate = LocalDate(2026, 6, 1),
            removalOrderDate = LocalDate(2026, 6, 10),
            orderNumber = "20-к",
            today = today,
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun removalBlankOrderNumberIsRejected() {
        val errors = Validation.removal(
            penaltyOrderDate = LocalDate(2026, 6, 1),
            removalOrderDate = LocalDate(2026, 6, 10),
            orderNumber = " ",
            today = today,
        )
        assertEquals(listOf("Номер приказа не должен быть пустым"), errors)
    }

    @Test
    fun removalTooLongOrderNumberIsRejected() {
        val errors = Validation.removal(
            penaltyOrderDate = LocalDate(2026, 6, 1),
            removalOrderDate = LocalDate(2026, 6, 10),
            orderNumber = "a".repeat(51),
            today = today,
        )
        assertEquals(listOf("Номер приказа не должен быть длиннее 50 символов"), errors)
    }

    @Test
    fun removalBeforePenaltyOrderDateIsRejected() {
        val errors = Validation.removal(
            penaltyOrderDate = LocalDate(2026, 6, 10),
            removalOrderDate = LocalDate(2026, 6, 1),
            orderNumber = "20-к",
            today = today,
        )
        assertEquals(listOf("Дата приказа о снятии не может быть раньше даты приказа о взыскании"), errors)
    }

    @Test
    fun removalAfterTodayIsRejected() {
        val errors = Validation.removal(
            penaltyOrderDate = LocalDate(2026, 6, 1),
            removalOrderDate = LocalDate(2026, 6, 20),
            orderNumber = "20-к",
            today = today,
        )
        assertEquals(listOf("Дата приказа о снятии не может быть позже текущей даты"), errors)
    }


    @Test
    fun userValidIsAccepted() {
        assertTrue(Validation.user("ivanov.hr", "strongPass1").isEmpty())
    }

    @Test
    fun userTooShortLoginIsRejected() {
        assertEquals(listOf("Логин должен быть от 3 до 50 символов"), Validation.user("ab", "strongPass1"))
    }

    @Test
    fun userTooLongLoginIsRejected() {
        assertEquals(listOf("Логин должен быть от 3 до 50 символов"), Validation.user("a".repeat(51), "strongPass1"))
    }

    @Test
    fun userLoginWithInvalidCharactersIsRejected() {
        assertEquals(
            listOf("Логин может содержать только латинские буквы, цифры, точку, дефис и подчёркивание"),
            Validation.user("иванов", "strongPass1"),
        )
    }

    @Test
    fun userTooShortPasswordIsRejected() {
        assertEquals(listOf("Пароль должен быть не короче 8 символов"), Validation.user("ivanov.hr", "short1"))
    }


    @Test
    fun periodValidIsAccepted() {
        assertTrue(Validation.period(LocalDate(2026, 1, 1), LocalDate(2026, 6, 30)).isEmpty())
    }

    @Test
    fun periodFromAfterToIsRejected() {
        val errors = Validation.period(LocalDate(2026, 6, 30), LocalDate(2026, 1, 1))
        assertEquals(listOf("Дата начала периода не может быть позже даты окончания"), errors)
    }
}
