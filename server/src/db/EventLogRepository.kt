package ru.haykor.sudv.db

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import ru.haykor.sudv.contract.StatusEventDto
import ru.haykor.sudv.domain.PenaltyId
import ru.haykor.sudv.domain.PenaltyStatus
import kotlin.time.Instant

interface IEventLogRepository {
    suspend fun add(penaltyId: PenaltyId, status: PenaltyStatus, at: Instant)

    /** Sorted by changed_at, then id. */
    suspend fun findByPenalty(penaltyId: PenaltyId): List<StatusEventDto>
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class EventLogRepository(private val transactor: Transactor) : IEventLogRepository {

    override suspend fun add(penaltyId: PenaltyId, status: PenaltyStatus, at: Instant) {
        transactor.tx {
            StatusEvents.insert {
                it[StatusEvents.penaltyId] = penaltyId.value
                it[StatusEvents.status] = status
                it[changedAt] = at
            }
        }
    }

    override suspend fun findByPenalty(penaltyId: PenaltyId): List<StatusEventDto> = transactor.tx {
        StatusEvents
            .selectAll()
            .where { StatusEvents.penaltyId eq penaltyId.value }
            .orderBy(StatusEvents.changedAt to SortOrder.ASC, StatusEvents.id to SortOrder.ASC)
            .toList()
            .map { StatusEventDto(status = it[StatusEvents.status], changedAt = it[StatusEvents.changedAt]) }
    }
}
