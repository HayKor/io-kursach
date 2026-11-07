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
import ru.haykor.sudv.shared.contract.DepartmentDto
import ru.haykor.sudv.shared.contract.EmployeeDto
import ru.haykor.sudv.shared.contract.EmployeeRequest
import ru.haykor.sudv.shared.domain.DepartmentId
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.Validation

/** Creates an employee or, with an id, edits one. */
interface EmployeeFormComponent {
    /** `null` for a new employee. */
    val employeeId: EmployeeId?
    val state: Value<State>

    fun onPersonnelNumberChange(value: String)
    fun onLastNameChange(value: String)
    fun onFirstNameChange(value: String)
    fun onMiddleNameChange(value: String)
    fun onPositionChange(value: String)
    fun onDepartmentChange(id: DepartmentId)
    fun onSave()
    fun onCancel()
    fun onRetry()

    data class State(
        val isNew: Boolean,
        /** Departments for the picker and, when editing, the employee's current data. */
        val prefill: LoadState<Unit> = LoadState.Loading,
        val departments: List<DepartmentDto> = emptyList(),
        val personnelNumber: String = "",
        val lastName: String = "",
        val firstName: String = "",
        val middleName: String = "",
        val position: String = "",
        val departmentId: DepartmentId? = null,
        val errors: List<String> = emptyList(),
        val isSaving: Boolean = false,
    )
}

class DefaultEmployeeFormComponent(
    componentContext: ComponentContext,
    private val apiClient: IApiClient,
    override val employeeId: EmployeeId?,
    private val onSaved: (EmployeeDto) -> Unit,
    private val onCancelled: () -> Unit,
) : EmployeeFormComponent, ComponentContext by componentContext {
    private val scope = coroutineScope()
    private val _state = MutableValue(EmployeeFormComponent.State(isNew = employeeId == null))
    override val state: Value<EmployeeFormComponent.State> = _state

    init {
        prefill()
    }

    override fun onPersonnelNumberChange(value: String) = edit { copy(personnelNumber = value) }
    override fun onLastNameChange(value: String) = edit { copy(lastName = value) }
    override fun onFirstNameChange(value: String) = edit { copy(firstName = value) }
    override fun onMiddleNameChange(value: String) = edit { copy(middleName = value) }
    override fun onPositionChange(value: String) = edit { copy(position = value) }
    override fun onDepartmentChange(id: DepartmentId) = edit { copy(departmentId = id) }

    override fun onSave() {
        val form = _state.value
        if (form.isSaving || form.prefill !is LoadState.Loaded) return
        val errors = Validation.employee(form.personnelNumber, form.lastName, form.firstName, form.middleName, form.position) +
            listOfNotNull("Выберите подразделение".takeIf { form.departmentId == null })
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        val request = EmployeeRequest(
            personnelNumber = form.personnelNumber.trim(),
            lastName = form.lastName.trim(),
            firstName = form.firstName.trim(),
            middleName = form.middleName.trim().ifEmpty { null },
            position = form.position.trim(),
            departmentId = form.departmentId!!,
        )
        _state.update { it.copy(isSaving = true, errors = emptyList()) }
        scope.launch {
            try {
                val saved = if (employeeId == null) {
                    apiClient.createEmployee(request)
                } else {
                    apiClient.updateEmployee(employeeId, request)
                }
                _state.update { it.copy(isSaving = false) }
                onSaved(saved)
            } catch (e: ApiClientException) {
                // E.g. a duplicate personnel number: the server knows about all employees, the form does not.
                _state.update { it.copy(isSaving = false, errors = listOf(e.message)) }
            }
        }
    }

    override fun onCancel() = onCancelled()

    override fun onRetry() = prefill()

    private fun prefill() {
        scope.launch {
            _state.update { it.copy(prefill = LoadState.Loading) }
            val result = load {
                val departments = apiClient.departments()
                val employee = employeeId?.let { apiClient.employee(it) }
                _state.update { form ->
                    val base = form.copy(departments = departments)
                    if (employee == null) {
                        base
                    } else {
                        base.copy(
                            personnelNumber = employee.personnelNumber,
                            lastName = employee.lastName,
                            firstName = employee.firstName,
                            middleName = employee.middleName.orEmpty(),
                            position = employee.position,
                            departmentId = employee.department.id,
                        )
                    }
                }
            }
            _state.update { it.copy(prefill = result) }
        }
    }

    // Any change clears the old errors: they described the previous input.
    private fun edit(change: EmployeeFormComponent.State.() -> EmployeeFormComponent.State) =
        _state.update { it.change().copy(errors = emptyList()) }
}
