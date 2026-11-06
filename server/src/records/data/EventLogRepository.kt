package ru.haykor.sudv.server.records.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import ru.haykor.sudv.server.core.database.Transactor
import ru.haykor.sudv.server.records.domain.IEventLogRepository
import ru.haykor.sudv.shared.contract.StatusEventDto
import ru.haykor.sudv.shared.domain.PenaltyId
import ru.haykor.sudv.shared.domain.PenaltyStatus
import kotlin.time.Instant

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
