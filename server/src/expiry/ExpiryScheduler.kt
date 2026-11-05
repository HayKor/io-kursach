package ru.haykor.sudv.expiry

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import ru.haykor.sudv.config.AppConfig
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** Daily penalty expiry run; disabled entirely when [AppConfig.expirySchedulerEnabled] is false. */
@Inject
@SingleIn(AppScope::class)
class ExpiryScheduler(
    private val expiryService: IExpiryService,
    private val appConfig: AppConfig,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {
    private var job: Job? = null

    fun start(application: Application) {
        if (!appConfig.expirySchedulerEnabled) return

        application.monitor.subscribe(ApplicationStarted) {
            job = application.launch { runLoop(application) }
        }
        application.monitor.subscribe(ApplicationStopping) {
            job?.cancel()
        }
    }

    private suspend fun runLoop(application: Application) {
        while (true) {
            runOnce(application)
            delay(delayUntilNextRun(clock.now(), appConfig.expiryRunAt, timeZone))
        }
    }

    // A failed run is logged and skipped; the loop keeps going so the next scheduled run still happens.
    private suspend fun runOnce(application: Application) {
        try {
            expiryService.expireDue(clock.todayIn(timeZone))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            application.log.error("Scheduled penalty expiry run failed", e)
        }
    }
}

/** Strictly positive delay until the next [runAt] in [timeZone] after [now]; [now] at or past today's [runAt] rolls over to tomorrow. */
internal fun delayUntilNextRun(now: Instant, runAt: LocalTime, timeZone: TimeZone): Duration {
    val today = now.toLocalDateTime(timeZone).date
    val todayRun = LocalDateTime(today, runAt).toInstant(timeZone)
    val nextRun = if (now < todayRun) todayRun else LocalDateTime(today.plus(1, DateTimeUnit.DAY), runAt).toInstant(timeZone)
    return nextRun - now
}
