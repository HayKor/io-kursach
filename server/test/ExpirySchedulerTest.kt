package ru.haykor.sudv.expiry

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ExpirySchedulerTest {
    private val timeZone = TimeZone.of("Europe/Moscow")
    private val runAt = LocalTime(0, 5)
    private val date = LocalDate(2026, 3, 10)

    private fun localInstant(hour: Int, minute: Int) = LocalDateTime(date, LocalTime(hour, minute)).toInstant(timeZone)

    @Test
    fun oneMinuteBeforeRunReturnsOneMinute() {
        val delay = delayUntilNextRun(localInstant(0, 4), runAt, timeZone)
        assertEquals(1.minutes, delay)
    }

    @Test
    fun exactlyAtRunRollsOverToTomorrow() {
        val delay = delayUntilNextRun(localInstant(0, 5), runAt, timeZone)
        assertEquals(24.hours, delay)
    }

    @Test
    fun lateEveningWaitsUntilEarlyMorning() {
        val delay = delayUntilNextRun(localInstant(23, 0), runAt, timeZone)
        assertEquals(1.hours + 5.minutes, delay)
    }
}
