package ru.haykor.sudv.client.ui.employees

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ru.haykor.sudv.client.domain.ApiClientException
import ru.haykor.sudv.client.domain.IApiClient
import ru.haykor.sudv.client.ui.common.LoadState
import ru.haykor.sudv.client.ui.common.load
import ru.haykor.sudv.client.ui.common.FileAction
import ru.haykor.sudv.client.ui.common.FileDelivery
import ru.haykor.sudv.client.ui.main.canEditEmployees
import ru.haykor.sudv.client.ui.main.canSeePenalties
import ru.haykor.sudv.shared.contract.EmployeeDto
import ru.haykor.sudv.shared.contract.PenaltyDto
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.ReportFormat
import ru.haykor.sudv.shared.domain.UserRole

interface EmployeeDetailsComponent {
    val employeeId: EmployeeId
    val state: Value<State>

    fun onEditClick()
    fun onArchiveClick()
    fun onArchiveConfirm()
    fun onArchiveDismiss()
    fun onCloseClick()
    fun onRetry()

    /** Downloads the personal card: the employee and all their penalties. */
    fun onExport(format: ReportFormat)
    fun onDelivered()

    /** Called by the section after the employee was edited. */
    fun reload()

    data class State(
        val employee: LoadState<EmployeeDto> = LoadState.Loading,
        /**
         * The personal card: penalties in chronological order. `null` for ADMIN, who may not see penalties; loaded in
         * the same request as [employee] otherwise.
         */
        val penalties: List<PenaltyDto>? = null,
        val canEdit: Boolean = false,
        val canSeePenalties: Boolean = false,
        val isExporting: Boolean = false,
        /** The exported card waiting for the platform to save it. */
        val delivery: FileDelivery? = null,
        val confirmingArchive: Boolean = false,
        val isArchiving: Boolean = false,
        val actionError: String? = null,
    ) {
        val canArchive: Boolean get() = canEdit && (employee as? LoadState.Loaded)?.data?.isArchived == false
    }
}

class DefaultEmployeeDetailsComponent(
    componentContext: ComponentContext,
    private val apiClient: IApiClient,
    override val employeeId: EmployeeId,
    role: UserRole,
    private val onEdit: () -> Unit,
    private val onChanged: () -> Unit,
    private val onClose: () -> Unit,
) : EmployeeDetailsComponent, ComponentContext by componentContext {
    private val scope = coroutineScope()
    private val _state = MutableValue(
        EmployeeDetailsComponent.State(canEdit = role.canEditEmployees, canSeePenalties = role.canSeePenalties),
    )
    override val state: Value<EmployeeDetailsComponent.State> = _state

    init {
        reload()
    }

    override fun onEditClick() = onEdit()

    override fun onArchiveClick() = _state.update { it.copy(confirmingArchive = true, actionError = null) }

    override fun onArchiveDismiss() = _state.update { it.copy(confirmingArchive = false) }

    override fun onArchiveConfirm() {
        _state.update { it.copy(confirmingArchive = false, isArchiving = true) }
        scope.launch {
            try {
                val archived = apiClient.archiveEmployee(employeeId)
                _state.update { it.copy(employee = LoadState.Loaded(archived), isArchiving = false) }
                onChanged()
            } catch (e: ApiClientException) {
                _state.update { it.copy(isArchiving = false, actionError = e.message) }
            }
        }
    }

    override fun onCloseClick() = onClose()

    override fun onRetry() = reload()

    override fun onExport(format: ReportFormat) {
        if (_state.value.isExporting) return
        _state.update { it.copy(isExporting = true, actionError = null) }
        scope.launch {
            try {
                val file = apiClient.exportEmployeeCard(employeeId, format)
                _state.update { it.copy(isExporting = false, delivery = FileDelivery(file, FileAction.SAVE)) }
            } catch (e: ApiClientException) {
                _state.update { it.copy(isExporting = false, actionError = e.message) }
            }
        }
    }

    override fun onDelivered() = _state.update { it.copy(delivery = null) }

    override fun reload() {
        scope.launch {
            _state.update { it.copy(employee = LoadState.Loading) }
            if (_state.value.canSeePenalties) {
                val result = load { apiClient.employeeCard(employeeId) }
                _state.update { state ->
                    when (result) {
                        is LoadState.Loaded -> state.copy(employee = LoadState.Loaded(result.data.employee), penalties = result.data.penalties)
                        is LoadState.Failed -> state.copy(employee = result)
                        LoadState.Loading -> state
                    }
                }
            } else {
                val result = load { apiClient.employee(employeeId) }
                _state.update { it.copy(employee = result) }
            }
        }
    }
}
