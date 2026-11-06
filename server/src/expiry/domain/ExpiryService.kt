package ru.haykor.sudv.server.expiry.domain

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import ru.haykor.sudv.server.core.database.Transactor
import ru.haykor.sudv.server.core.error.ApiException
import ru.haykor.sudv.server.records.domain.IEventLogRepository
import ru.haykor.sudv.server.records.domain.IPenaltyRepository
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.contract.PenaltyDto
import ru.haykor.sudv.shared.contract.PenaltyRemovalDto
import ru.haykor.sudv.shared.domain.PenaltyId
import ru.haykor.sudv.shared.domain.PenaltyRules
import ru.haykor.sudv.shared.domain.PenaltyStatus
import ru.haykor.sudv.shared.domain.Validation
import kotlin.time.Clock

interface IExpiryService {
    /** Expires every ACTIVE penalty due by [today]. Returns how many were expired; re-running on the same day is a no-op. */
    suspend fun expireDue(today: LocalDate): Int

    /** Returns the penalty with [PenaltyDto.removal] filled in from [removal]. */
    suspend fun removeEarly(id: PenaltyId, removal: PenaltyRemovalDto): PenaltyDto
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ExpiryService(
    private val penaltyRepository: IPenaltyRepository,
    private val eventLogRepository: IEventLogRepository,
    private val transactor: Transactor,
    private val clock: Clock,
    private val timeZone: TimeZone,
) : IExpiryService {

    override suspend fun expireDue(today: LocalDate): Int {
        var expired = 0
        for ((id, orderDate) in penaltyRepository.findActive()) {
            if (!PenaltyRules.isDue(orderDate, today)) continue
            transactor.tx {
                // changeStatus loses the race to a concurrent early removal instead of overwriting it.
                if (penaltyRepository.changeStatus(id, PenaltyStatus.ACTIVE, PenaltyStatus.EXPIRED)) {
                    eventLogRepository.add(id, PenaltyStatus.EXPIRED, clock.now())
                    expired++
                }
            }
        }
        return expired
    }

    override suspend fun removeEarly(id: PenaltyId, removal: PenaltyRemovalDto): PenaltyDto {
        val penalty = penaltyRepository.findById(id) ?: throw ApiException(ErrorCode.NOT_FOUND, "Взыскание не найдено")
        if (!PenaltyRules.canRemoveEarly(penalty.status)) {
            throw ApiException(ErrorCode.PENALTY_NOT_ACTIVE, "Взыскание уже не действует")
        }

        val today = clock.todayIn(timeZone)
        val errors = Validation.removal(penalty.orderDate, removal.orderDate, removal.orderNumber, today)
        if (errors.isNotEmpty()) throw ApiException(ErrorCode.VALIDATION_FAILED, errors.joinToString("; "))

        return transactor.tx {
            // Re-checked atomically: expireDue() may have won the race since the check above.
            if (!penaltyRepository.changeStatus(id, PenaltyStatus.ACTIVE, PenaltyStatus.REMOVED)) {
                throw ApiException(ErrorCode.PENALTY_NOT_ACTIVE, "Взыскание уже не действует")
            }
            penaltyRepository.saveRemoval(id, removal)
            eventLogRepository.add(id, PenaltyStatus.REMOVED, clock.now())
            penaltyRepository.findById(id) ?: error("Penalty $id disappeared right after removal")
        }
    }
}
