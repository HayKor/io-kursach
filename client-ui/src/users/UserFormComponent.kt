package ru.haykor.sudv.client.ui.users

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
import ru.haykor.sudv.shared.contract.CreateUserRequest
import ru.haykor.sudv.shared.contract.EmployeeDto
import ru.haykor.sudv.shared.contract.UserDto
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.UserRole
import ru.haykor.sudv.shared.domain.Validation

/** Creates an account for an active employee who does not have one yet. */
interface UserFormComponent {
    val state: Value<State>

    fun onEmployeeChange(id: EmployeeId)
    fun onRoleChange(role: UserRole)
    fun onLoginChange(value: String)
    fun onPasswordChange(value: String)
    fun onSave()
    fun onCancel()
    fun onRetry()

    data class State(
        /** Active employees without an account: an archived one cannot sign in, and an employee has one account. */
        val employees: LoadState<List<EmployeeDto>> = LoadState.Loading,
        val employeeId: EmployeeId? = null,
        val role: UserRole? = null,
        val login: String = "",
        val password: String = "",
        val errors: List<String> = emptyList(),
        val isSaving: Boolean = false,
    )
}

class DefaultUserFormComponent(
    componentContext: ComponentContext,
    private val apiClient: IApiClient,
    private val onSaved: (UserDto) -> Unit,
    private val onCancelled: () -> Unit,
) : UserFormComponent, ComponentContext by componentContext {
    private val scope = coroutineScope()
    private val _state = MutableValue(UserFormComponent.State())
    override val state: Value<UserFormComponent.State> = _state

    init {
        loadEmployees()
    }

    override fun onEmployeeChange(id: EmployeeId) = edit { copy(employeeId = id) }
    override fun onRoleChange(role: UserRole) = edit { copy(role = role) }
    override fun onLoginChange(value: String) = edit { copy(login = value) }
    override fun onPasswordChange(value: String) = edit { copy(password = value) }

    override fun onSave() {
        val form = _state.value
        if (form.isSaving || form.employees !is LoadState.Loaded) return
        val errors = listOfNotNull(
            "Выберите сотрудника".takeIf { form.employeeId == null },
            "Выберите роль".takeIf { form.role == null },
        ) + Validation.user(form.login, form.password)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(isSaving = true, errors = emptyList()) }
        scope.launch {
            try {
                val created = apiClient.createUser(
                    CreateUserRequest(form.login.trim(), form.password, form.role!!, form.employeeId!!),
                )
                _state.update { it.copy(isSaving = false) }
                onSaved(created)
            } catch (e: ApiClientException) {
                // E.g. a login already taken: only the server knows all accounts.
                _state.update { it.copy(isSaving = false, errors = listOf(e.message)) }
            }
        }
    }

    override fun onCancel() = onCancelled()

    override fun onRetry() = loadEmployees()

    private fun loadEmployees() {
        scope.launch {
            _state.update { it.copy(employees = LoadState.Loading) }
            val result = load {
                val taken = apiClient.users().map { it.employee.id }.toSet()
                apiClient.employees().filter { it.id !in taken }
            }
            _state.update { it.copy(employees = result) }
        }
    }

    // Any change clears the old errors: they described the previous input.
    private fun edit(change: UserFormComponent.State.() -> UserFormComponent.State) =
        _state.update { it.change().copy(errors = emptyList()) }
}
