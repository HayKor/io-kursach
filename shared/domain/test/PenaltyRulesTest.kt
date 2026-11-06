package ru.haykor.sudv.shared.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PenaltyRulesTest {
    @Test
    fun expiryDateIsOneYearAfterOrder() {
        assertEquals(LocalDate(2027, 3, 12), PenaltyRules.expiryDate(LocalDate(2026, 3, 12)))
    }

    @Test
    fun expiryDateFromLeapDayFallsBackToFebruary28() {
        assertEquals(LocalDate(2029, 2, 28), PenaltyRules.expiryDate(LocalDate(2028, 2, 29)))
    }

    @Test
    fun isDueIsFalseTheDayBeforeExpiry() {
        val orderDate = LocalDate(2026, 3, 12)
        val expiry = PenaltyRules.expiryDate(orderDate)
        assertFalse(PenaltyRules.isDue(orderDate, expiry.minus(1, DateTimeUnit.DAY)))
    }

    @Test
    fun isDueIsTrueOnExpiryDate() {
        val orderDate = LocalDate(2026, 3, 12)
        assertTrue(PenaltyRules.isDue(orderDate, PenaltyRules.expiryDate(orderDate)))
    }

    @Test
    fun isDueIsTrueAfterExpiryDate() {
        val orderDate = LocalDate(2026, 3, 12)
        assertTrue(PenaltyRules.isDue(orderDate, LocalDate(2028, 1, 1)))
    }

    @Test
    fun remainingDaysCountsDownToExpiry() {
        val orderDate = LocalDate(2026, 1, 1)
        assertEquals(1, PenaltyRules.remainingDays(orderDate, LocalDate(2026, 12, 31)))
    }

    @Test
    fun remainingDaysIsZeroOnExpiryDate() {
        val orderDate = LocalDate(2026, 1, 1)
        assertEquals(0, PenaltyRules.remainingDays(orderDate, PenaltyRules.expiryDate(orderDate)))
    }

    @Test
    fun remainingDaysIsZeroAfterExpiryDate() {
        val orderDate = LocalDate(2026, 1, 1)
        assertEquals(0, PenaltyRules.remainingDays(orderDate, LocalDate(2028, 1, 1)))
    }

    @Test
    fun canRemoveEarlyOnlyWhenActive() {
        assertTrue(PenaltyRules.canRemoveEarly(PenaltyStatus.ACTIVE))
        assertFalse(PenaltyRules.canRemoveEarly(PenaltyStatus.EXPIRED))
        assertFalse(PenaltyRules.canRemoveEarly(PenaltyStatus.REMOVED))
    }
}
