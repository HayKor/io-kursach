package ru.haykor.sudv.server.records.domain

import ru.haykor.sudv.shared.contract.StatusEventDto
import ru.haykor.sudv.shared.domain.PenaltyId
import ru.haykor.sudv.shared.domain.PenaltyStatus
import kotlin.time.Instant

interface IEventLogRepository {
    suspend fun add(penaltyId: PenaltyId, status: PenaltyStatus, at: Instant)

    /** Sorted by changed_at, then id. */
    suspend fun findByPenalty(penaltyId: PenaltyId): List<StatusEventDto>
}
