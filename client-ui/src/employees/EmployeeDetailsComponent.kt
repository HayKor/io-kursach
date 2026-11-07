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
import ru.haykor.sudv.client.ui.main.canEditEmployees
import ru.haykor.sudv.shared.contract.EmployeeDto
import ru.haykor.sudv.shared.domain.EmployeeId
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

    /** Called by the section after the employee was edited. */
    fun reload()

    data class State(
        val employee: LoadState<EmployeeDto> = LoadState.Loading,
        val canEdit: Boolean = false,
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
    private val _state = MutableValue(EmployeeDetailsComponent.State(canEdit = role.canEditEmployees))
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

    override fun reload() {
        scope.launch {
            _state.update { it.copy(employee = LoadState.Loading) }
            val result = load { apiClient.employee(employeeId) }
            _state.update { it.copy(employee = result) }
        }
    }
}
