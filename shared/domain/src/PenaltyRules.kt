package ru.haykor.sudv.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

object PenaltyRules {
    fun expiryDate(orderDate: LocalDate): LocalDate = orderDate.plus(1, DateTimeUnit.YEAR)

    fun isDue(orderDate: LocalDate, today: LocalDate): Boolean = today >= expiryDate(orderDate)

    fun remainingDays(orderDate: LocalDate, today: LocalDate): Int =
        maxOf(0, today.daysUntil(expiryDate(orderDate)))

    fun canRemoveEarly(status: PenaltyStatus): Boolean = status == PenaltyStatus.ACTIVE
}
