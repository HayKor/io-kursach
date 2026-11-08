package ru.haykor.sudv.client.ui.employees

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.haykor.sudv.client.domain.IApiClient
import ru.haykor.sudv.client.ui.common.LoadState
import ru.haykor.sudv.client.ui.common.load
import ru.haykor.sudv.client.ui.main.canCreateEmployees
import ru.haykor.sudv.client.ui.main.seesAllDepartments
import ru.haykor.sudv.shared.contract.DepartmentDto
import ru.haykor.sudv.shared.contract.EmployeeDto
import ru.haykor.sudv.shared.domain.DepartmentId
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.UserRole

interface EmployeeListComponent {
    val state: Value<State>

    fun onQueryChange(query: String)
    fun onDepartmentChange(id: DepartmentId?)
    fun onIncludeArchivedChange(include: Boolean)
    fun onRefresh()
    fun onEmployeeClick(id: EmployeeId)
    fun onCreateClick()

    /** Called by the section after an employee was created, edited or archived. */
    fun reload()

    data class State(
        val query: String = "",
        val departmentId: DepartmentId? = null,
        val includeArchived: Boolean = false,
        val departments: List<DepartmentDto> = emptyList(),
        val employees: LoadState<List<EmployeeDto>> = LoadState.Loading,
        val canFilterByDepartment: Boolean = false,
        val canCreate: Boolean = false,
    )
}

/** Typing waits this long before the search goes to the server, so every key press is not a request. */
internal const val SEARCH_DEBOUNCE_MS = 300L

class DefaultEmployeeListComponent(
    componentContext: ComponentContext,
    private val apiClient: IApiClient,
    role: UserRole,
    private val onOpen: (EmployeeId) -> Unit,
    private val onCreate: () -> Unit,
) : EmployeeListComponent, ComponentContext by componentContext {
    private val scope = coroutineScope()
    private var loadJob: Job? = null

    private val _state = MutableValue(
        EmployeeListComponent.State(canFilterByDepartment = role.seesAllDepartments, canCreate = role.canCreateEmployees),
    )
    override val state: Value<EmployeeListComponent.State> = _state

    init {
        if (role.seesAllDepartments) {
            scope.launch {
                // The filter is optional: without departments it simply has nothing to offer.
                (load { apiClient.departments() } as? LoadState.Loaded)?.let { loaded ->
                    _state.update { it.copy(departments = loaded.data) }
                }
            }
        }
        reload()
    }

    override fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        reload(debounce = true)
    }

    override fun onDepartmentChange(id: DepartmentId?) {
        _state.update { it.copy(departmentId = id) }
        reload()
    }

    override fun onIncludeArchivedChange(include: Boolean) {
        _state.update { it.copy(includeArchived = include) }
        reload()
    }

    override fun onRefresh() = reload()

    override fun onEmployeeClick(id: EmployeeId) = onOpen(id)

    override fun onCreateClick() = onCreate()

    override fun reload() = reload(debounce = false)

    // The newest request wins: a slow earlier answer must not overwrite the result of a later filter.
    private fun reload(debounce: Boolean) {
        loadJob?.cancel()
        loadJob = scope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            // Keep the shown list while refreshing; the spinner is only for the very first load or after an error.
            if (_state.value.employees !is LoadState.Loaded) _state.update { it.copy(employees = LoadState.Loading) }
            val filter = _state.value
            val result = load {
                apiClient.employees(
                    query = filter.query.trim().ifEmpty { null },
                    departmentId = filter.departmentId,
                    includeArchived = filter.includeArchived,
                )
            }
            _state.update { it.copy(employees = result) }
        }
    }
}
