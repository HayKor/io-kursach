package ru.haykor.sudv.shared.domain

import kotlinx.datetime.LocalDate

private val LOGIN_PATTERN = Regex("[A-Za-z0-9._-]+")

object Validation {
    fun employee(
        personnelNumber: String,
        lastName: String,
        firstName: String,
        middleName: String?,
        position: String,
    ): List<String> {
        val errors = mutableListOf<String>()
        val trimmedPersonnelNumber = personnelNumber.trim()
        val trimmedLastName = lastName.trim()
        val trimmedFirstName = firstName.trim()
        val trimmedMiddleName = middleName?.trim()
        val trimmedPosition = position.trim()

        if (trimmedPersonnelNumber.isEmpty()) {
            errors += "Табельный номер не должен быть пустым"
        } else if (trimmedPersonnelNumber.length > 20) {
            errors += "Табельный номер не должен быть длиннее 20 символов"
        }

        if (trimmedLastName.isEmpty()) {
            errors += "Фамилия не должна быть пустой"
        } else if (trimmedLastName.length > 100) {
            errors += "Фамилия не должна быть длиннее 100 символов"
        }

        if (trimmedFirstName.isEmpty()) {
            errors += "Имя не должно быть пустым"
        } else if (trimmedFirstName.length > 100) {
            errors += "Имя не должно быть длиннее 100 символов"
        }

        if (trimmedMiddleName != null && trimmedMiddleName.length > 100) {
            errors += "Отчество не должно быть длиннее 100 символов"
        }

        if (trimmedPosition.isEmpty()) {
            errors += "Должность не должна быть пустой"
        } else if (trimmedPosition.length > 200) {
            errors += "Должность не должна быть длиннее 200 символов"
        }

        return errors
    }

    fun penalty(
        violationDate: LocalDate,
        orderDate: LocalDate,
        description: String,
        orderNumber: String,
        today: LocalDate,
    ): List<String> {
        val errors = mutableListOf<String>()
        val trimmedDescription = description.trim()
        val trimmedOrderNumber = orderNumber.trim()

        if (trimmedDescription.isEmpty()) {
            errors += "Описание не должно быть пустым"
        }

        if (trimmedOrderNumber.isEmpty()) {
            errors += "Номер приказа не должен быть пустым"
        } else if (trimmedOrderNumber.length > 50) {
            errors += "Номер приказа не должен быть длиннее 50 символов"
        }

        if (violationDate > orderDate) {
            errors += "Дата нарушения не может быть позже даты приказа"
        }
        if (orderDate > today) {
            errors += "Дата приказа не может быть позже текущей даты"
        }

        return errors
    }

    fun removal(
        penaltyOrderDate: LocalDate,
        removalOrderDate: LocalDate,
        orderNumber: String,
        today: LocalDate,
    ): List<String> {
        val errors = mutableListOf<String>()
        val trimmedOrderNumber = orderNumber.trim()

        if (trimmedOrderNumber.isEmpty()) {
            errors += "Номер приказа не должен быть пустым"
        } else if (trimmedOrderNumber.length > 50) {
            errors += "Номер приказа не должен быть длиннее 50 символов"
        }

        if (removalOrderDate < penaltyOrderDate) {
            errors += "Дата приказа о снятии не может быть раньше даты приказа о взыскании"
        }
        if (removalOrderDate > today) {
            errors += "Дата приказа о снятии не может быть позже текущей даты"
        }

        return errors
    }

    fun user(login: String, password: String): List<String> {
        val errors = mutableListOf<String>()
        val trimmedLogin = login.trim()

        if (trimmedLogin.length < 3 || trimmedLogin.length > 50) {
            errors += "Логин должен быть от 3 до 50 символов"
        } else if (!LOGIN_PATTERN.matches(trimmedLogin)) {
            errors += "Логин может содержать только латинские буквы, цифры, точку, дефис и подчёркивание"
        }

        if (password.length < 8) {
            errors += "Пароль должен быть не короче 8 символов"
        }

        return errors
    }

    fun period(from: LocalDate, to: LocalDate): List<String> {
        if (from > to) {
            return listOf("Дата начала периода не может быть позже даты окончания")
        }
        return emptyList()
    }
}
