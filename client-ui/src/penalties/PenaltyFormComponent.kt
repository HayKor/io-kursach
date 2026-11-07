package ru.haykor.sudv.client.ui.penalties

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import ru.haykor.sudv.client.domain.ApiClientException
import ru.haykor.sudv.client.domain.IApiClient
import ru.haykor.sudv.client.ui.common.LoadState
import ru.haykor.sudv.client.ui.common.load
import ru.haykor.sudv.shared.contract.CreatePenaltyRequest
import ru.haykor.sudv.shared.contract.EmployeeDto
import ru.haykor.sudv.shared.contract.PenaltyDto
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.PenaltyRules
import ru.haykor.sudv.shared.domain.PenaltyType
import ru.haykor.sudv.shared.domain.Validation
import kotlin.time.Clock

/** Registers a penalty for an active employee. */
interface PenaltyFormComponent {
    val state: Value<State>

    fun onEmployeeChange(id: EmployeeId)
    fun onTypeChange(type: PenaltyType)
    fun onViolationDateChange(date: LocalDate)
    fun onOrderDateChange(date: LocalDate)
    fun onDescriptionChange(value: String)
    fun onOrderNumberChange(value: String)
    fun onSave()
    fun onCancel()
    fun onRetry()

    data class State(
        val today: LocalDate,
        /** Active employees: the server refuses a penalty for an archived one. */
        val employees: LoadState<List<EmployeeDto>> = LoadState.Loading,
        val employeeId: EmployeeId? = null,
        val type: PenaltyType? = null,
        val violationDate: LocalDate? = null,
        val orderDate: LocalDate? = null,
        val description: String = "",
        val orderNumber: String = "",
        val errors: List<String> = emptyList(),
        val isSaving: Boolean = false,
    ) {
        /** Shown under the order date, so the user sees the consequence before saving. */
        val expiryDate: LocalDate? get() = orderDate?.let(PenaltyRules::expiryDate)
    }
}

class DefaultPenaltyFormComponent(
    componentContext: ComponentContext,
    private val apiClient: IApiClient,
    private val onSaved: (PenaltyDto) -> Unit,
    private val onCancelled: () -> Unit,
    clock: Clock = Clock.System,
) : PenaltyFormComponent, ComponentContext by componentContext {
    private val scope = coroutineScope()
    private val _state = MutableValue(PenaltyFormComponent.State(today = clock.todayIn(TimeZone.currentSystemDefault())))
    override val state: Value<PenaltyFormComponent.State> = _state

    init {
        loadEmployees()
    }

    override fun onEmployeeChange(id: EmployeeId) = edit { copy(employeeId = id) }
    override fun onTypeChange(type: PenaltyType) = edit { copy(type = type) }
    override fun onViolationDateChange(date: LocalDate) = edit { copy(violationDate = date) }
    override fun onOrderDateChange(date: LocalDate) = edit { copy(orderDate = date) }
    override fun onDescriptionChange(value: String) = edit { copy(description = value) }
    override fun onOrderNumberChange(value: String) = edit { copy(orderNumber = value) }

    override fun onSave() {
        val form = _state.value
        if (form.isSaving || form.employees !is LoadState.Loaded) return
        val missing = listOfNotNull(
            "Выберите сотрудника".takeIf { form.employeeId == null },
            "Выберите вид взыскания".takeIf { form.type == null },
            "Укажите дату нарушения".takeIf { form.violationDate == null },
            "Укажите дату приказа".takeIf { form.orderDate == null },
        )
        // The date rules need both dates, so they are checked only once the dates are there.
        val errors = missing.ifEmpty {
            Validation.penalty(form.violationDate!!, form.orderDate!!, form.description, form.orderNumber, form.today)
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        val request = CreatePenaltyRequest(
            employeeId = form.employeeId!!,
            type = form.type!!,
            violationDate = form.violationDate!!,
            description = form.description.trim(),
            orderNumber = form.orderNumber.trim(),
            orderDate = form.orderDate!!,
        )
        _state.update { it.copy(isSaving = true, errors = emptyList()) }
        scope.launch {
            try {
                val created = apiClient.createPenalty(request)
                _state.update { it.copy(isSaving = false) }
                onSaved(created)
            } catch (e: ApiClientException) {
                // E.g. the employee was archived meanwhile: the server has the final word.
                _state.update { it.copy(isSaving = false, errors = listOf(e.message)) }
            }
        }
    }

    override fun onCancel() = onCancelled()

    override fun onRetry() = loadEmployees()

    private fun loadEmployees() {
        scope.launch {
            _state.update { it.copy(employees = LoadState.Loading) }
            val result = load { apiClient.employees() }
            _state.update { it.copy(employees = result) }
        }
    }

    // Any change clears the old errors: they described the previous input.
    private fun edit(change: PenaltyFormComponent.State.() -> PenaltyFormComponent.State) =
        _state.update { it.change().copy(errors = emptyList()) }
}
