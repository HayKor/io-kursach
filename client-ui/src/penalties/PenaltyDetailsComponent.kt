package ru.haykor.sudv.client.ui.penalties

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import ru.haykor.sudv.client.domain.ApiClientException
import ru.haykor.sudv.client.domain.ApiException
import ru.haykor.sudv.client.domain.IApiClient
import ru.haykor.sudv.client.ui.common.LoadState
import ru.haykor.sudv.client.ui.common.load
import ru.haykor.sudv.client.ui.main.canManagePenalties
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.contract.PenaltyDetailsDto
import ru.haykor.sudv.shared.contract.PenaltyRemovalDto
import ru.haykor.sudv.shared.domain.PenaltyId
import ru.haykor.sudv.shared.domain.PenaltyRules
import ru.haykor.sudv.shared.domain.PenaltyStatus
import ru.haykor.sudv.shared.domain.UserRole
import ru.haykor.sudv.shared.domain.Validation
import kotlin.time.Clock

/** A penalty's card: its data, expiry and status history; HR can remove an active one early. */
interface PenaltyDetailsComponent {
    val penaltyId: PenaltyId
    val state: Value<State>
    val attachments: AttachmentsComponent

    fun onRemoveClick()
    fun onRemovalOrderNumberChange(value: String)
    fun onRemovalOrderDateChange(date: LocalDate)
    fun onRemovalConfirm()
    fun onRemovalDismiss()
    fun onCloseClick()
    fun onRetry()

    data class State(
        val today: LocalDate,
        val details: LoadState<PenaltyDetailsDto> = LoadState.Loading,
        val canManage: Boolean = false,
        /** The open early removal dialog, `null` when it is closed. */
        val removal: RemovalForm? = null,
    ) {
        private val penalty get() = (details as? LoadState.Loaded)?.data?.penalty

        val canRemove: Boolean get() = canManage && penalty?.status?.let(PenaltyRules::canRemoveEarly) == true

        /** Days until the penalty expires; only an active one has them. */
        val remainingDays: Int?
            get() = penalty?.takeIf { it.status == PenaltyStatus.ACTIVE }?.let { PenaltyRules.remainingDays(it.orderDate, today) }
    }

    data class RemovalForm(
        val orderNumber: String = "",
        val orderDate: LocalDate? = null,
        val errors: List<String> = emptyList(),
        val isSaving: Boolean = false,
    )
}

class DefaultPenaltyDetailsComponent(
    componentContext: ComponentContext,
    private val apiClient: IApiClient,
    override val penaltyId: PenaltyId,
    role: UserRole,
    private val onChanged: () -> Unit,
    private val onClose: () -> Unit,
    clock: Clock = Clock.System,
) : PenaltyDetailsComponent, ComponentContext by componentContext {
    private val scope = coroutineScope()
    private val _state = MutableValue(
        PenaltyDetailsComponent.State(today = clock.todayIn(TimeZone.currentSystemDefault()), canManage = role.canManagePenalties),
    )
    override val state: Value<PenaltyDetailsComponent.State> = _state

    private val _attachments = DefaultAttachmentsComponent(
        componentContext = childContext("attachments"),
        apiClient = apiClient,
        penaltyId = penaltyId,
        canEdit = role.canManagePenalties,
    )
    override val attachments: AttachmentsComponent = _attachments

    init {
        reload()
    }

    override fun onRemoveClick() {
        if (_state.value.canRemove) _state.update { it.copy(removal = PenaltyDetailsComponent.RemovalForm(orderDate = it.today)) }
    }

    override fun onRemovalOrderNumberChange(value: String) = editRemoval { copy(orderNumber = value) }

    override fun onRemovalOrderDateChange(date: LocalDate) = editRemoval { copy(orderDate = date) }

    override fun onRemovalDismiss() {
        if (_state.value.removal?.isSaving != true) _state.update { it.copy(removal = null) }
    }

    override fun onRemovalConfirm() {
        val shown = _state.value
        val form = shown.removal ?: return
        val penalty = (shown.details as? LoadState.Loaded)?.data?.penalty ?: return
        if (form.isSaving) return
        val errors = if (form.orderDate == null) {
            listOf("Укажите дату приказа о снятии")
        } else {
            Validation.removal(penalty.orderDate, form.orderDate, form.orderNumber, shown.today)
        }
        if (errors.isNotEmpty()) {
            updateRemoval { copy(errors = errors) }
            return
        }
        updateRemoval { copy(isSaving = true, errors = emptyList()) }
        scope.launch {
            try {
                apiClient.removePenalty(penaltyId, PenaltyRemovalDto(form.orderNumber.trim(), form.orderDate!!))
                _state.update { it.copy(removal = null) }
                onChanged()
                // The removal also adds an event to the history, so the whole card is fetched again.
                reload()
            } catch (e: ApiClientException) {
                updateRemoval { copy(isSaving = false, errors = listOf(e.message)) }
                // Someone else changed the status meanwhile (or the scheduler expired it): show the current one.
                if (e is ApiException && e.code == ErrorCode.PENALTY_NOT_ACTIVE) {
                    onChanged()
                    reload()
                }
            }
        }
    }

    override fun onCloseClick() = onClose()

    override fun onRetry() = reload()

    private fun reload() {
        scope.launch {
            if (_state.value.details !is LoadState.Loaded) _state.update { it.copy(details = LoadState.Loading) }
            val result = load { apiClient.penalty(penaltyId) }
            (result as? LoadState.Loaded)?.let { _attachments.setAttachments(it.data.attachments) }
            _state.update { it.copy(details = result) }
        }
    }

    // Any change of the input clears the old errors: they described the previous input.
    private fun editRemoval(change: PenaltyDetailsComponent.RemovalForm.() -> PenaltyDetailsComponent.RemovalForm) =
        updateRemoval { change().copy(errors = emptyList()) }

    private fun updateRemoval(change: PenaltyDetailsComponent.RemovalForm.() -> PenaltyDetailsComponent.RemovalForm) =
        _state.update { state -> state.copy(removal = state.removal?.change()) }
}
