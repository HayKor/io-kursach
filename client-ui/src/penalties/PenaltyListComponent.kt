package ru.haykor.sudv.client.ui.penalties

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import ru.haykor.sudv.client.domain.ApiClientException
import ru.haykor.sudv.client.domain.IApiClient
import ru.haykor.sudv.client.ui.common.FileAction
import ru.haykor.sudv.client.ui.common.FileDelivery
import ru.haykor.sudv.client.ui.common.LoadState
import ru.haykor.sudv.client.ui.common.load
import ru.haykor.sudv.client.ui.employees.SEARCH_DEBOUNCE_MS
import ru.haykor.sudv.client.ui.main.canManagePenalties
import ru.haykor.sudv.client.ui.main.seesAllDepartments
import ru.haykor.sudv.shared.contract.DepartmentDto
import ru.haykor.sudv.shared.contract.PenaltyDto
import ru.haykor.sudv.shared.contract.PenaltyFilter
import ru.haykor.sudv.shared.contract.PenaltySortField
import ru.haykor.sudv.shared.contract.SortDirection
import ru.haykor.sudv.shared.domain.DepartmentId
import ru.haykor.sudv.shared.domain.PenaltyId
import ru.haykor.sudv.shared.domain.PenaltyStatus
import ru.haykor.sudv.shared.domain.ReportFormat
import ru.haykor.sudv.shared.domain.UserRole

/** The registry: penalties filtered and sorted by the server. */
interface PenaltyListComponent {
    val state: Value<State>

    fun onEmployeeNameChange(value: String)
    fun onStatusChange(status: PenaltyStatus?)
    fun onDepartmentChange(id: DepartmentId?)
    fun onOrderDateFromChange(date: LocalDate?)
    fun onOrderDateToChange(date: LocalDate?)
    fun onSortChange(field: PenaltySortField)
    fun onDirectionToggle()
    fun onFiltersToggle()
    fun onRefresh()
    fun onPenaltyClick(id: PenaltyId)
    fun onCreateClick()

    /** Downloads the registry with the shown filters and sort, as on the screen. */
    fun onExport(format: ReportFormat)
    fun onDelivered()

    /** Called by the section after a penalty was registered or removed. */
    fun reload()

    data class State(
        val employeeName: String = "",
        val status: PenaltyStatus? = null,
        val departmentId: DepartmentId? = null,
        val orderDateFrom: LocalDate? = null,
        val orderDateTo: LocalDate? = null,
        val sort: PenaltySortField = PenaltyFilter().sort,
        val direction: SortDirection = PenaltyFilter().direction,
        /** On a phone the filters take most of the screen, so they fold away behind a button. */
        val filtersShown: Boolean = false,
        val departments: List<DepartmentDto> = emptyList(),
        val penalties: LoadState<List<PenaltyDto>> = LoadState.Loading,
        val canFilterByDepartment: Boolean = false,
        val canCreate: Boolean = false,
        val isExporting: Boolean = false,
        val exportError: String? = null,
        /** The exported file waiting for the platform to save it. */
        val delivery: FileDelivery? = null,
    ) {
        /** What goes to the server: the shown filters, the name trimmed and an empty one left out. */
        val filter: PenaltyFilter
            get() = PenaltyFilter(
                status = status,
                employeeName = employeeName.trim().ifEmpty { null },
                departmentId = departmentId,
                orderDateFrom = orderDateFrom,
                orderDateTo = orderDateTo,
                sort = sort,
                direction = direction,
            )

        /** Filters other than the name and the sort, counted on the folded filters button. */
        val activeFilterCount: Int
            get() = listOfNotNull(status, departmentId, orderDateFrom, orderDateTo).size
    }
}

class DefaultPenaltyListComponent(
    componentContext: ComponentContext,
    private val apiClient: IApiClient,
    role: UserRole,
    private val onOpen: (PenaltyId) -> Unit,
    private val onCreate: () -> Unit,
) : PenaltyListComponent, ComponentContext by componentContext {
    private val scope = coroutineScope()
    private var loadJob: Job? = null

    private val _state = MutableValue(
        PenaltyListComponent.State(canFilterByDepartment = role.seesAllDepartments, canCreate = role.canManagePenalties),
    )
    override val state: Value<PenaltyListComponent.State> = _state

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

    override fun onEmployeeNameChange(value: String) {
        _state.update { it.copy(employeeName = value) }
        reload(debounce = true)
    }

    override fun onStatusChange(status: PenaltyStatus?) = filter { copy(status = status) }
    override fun onDepartmentChange(id: DepartmentId?) = filter { copy(departmentId = id) }
    override fun onOrderDateFromChange(date: LocalDate?) = filter { copy(orderDateFrom = date) }
    override fun onOrderDateToChange(date: LocalDate?) = filter { copy(orderDateTo = date) }
    override fun onSortChange(field: PenaltySortField) = filter { copy(sort = field) }

    override fun onDirectionToggle() = filter {
        copy(direction = if (direction == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC)
    }

    override fun onFiltersToggle() = _state.update { it.copy(filtersShown = !it.filtersShown) }

    override fun onRefresh() = reload()

    override fun onPenaltyClick(id: PenaltyId) = onOpen(id)

    override fun onCreateClick() = onCreate()

    override fun onExport(format: ReportFormat) {
        val shown = _state.value
        if (shown.isExporting) return
        _state.update { it.copy(isExporting = true, exportError = null) }
        scope.launch {
            try {
                val file = apiClient.exportPenalties(shown.filter, format)
                _state.update { it.copy(isExporting = false, delivery = FileDelivery(file, FileAction.SAVE)) }
            } catch (e: ApiClientException) {
                _state.update { it.copy(isExporting = false, exportError = e.message) }
            }
        }
    }

    override fun onDelivered() = _state.update { it.copy(delivery = null) }

    override fun reload() = reload(debounce = false)

    private fun filter(change: PenaltyListComponent.State.() -> PenaltyListComponent.State) {
        _state.update(change)
        reload()
    }

    // The newest request wins: a slow earlier answer must not overwrite the result of a later filter.
    private fun reload(debounce: Boolean) {
        loadJob?.cancel()
        val shown = _state.value
        val from = shown.orderDateFrom
        val to = shown.orderDateTo
        if (from != null && to != null && from > to) {
            // Asking the server would only return an empty list; say what is wrong instead.
            _state.update { it.copy(penalties = LoadState.Failed("Начало периода позже его конца", canRetry = false)) }
            return
        }
        loadJob = scope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            // Keep the shown list while refreshing; the spinner is only for the very first load or after an error.
            if (_state.value.penalties !is LoadState.Loaded) _state.update { it.copy(penalties = LoadState.Loading) }
            val result = load { apiClient.penalties(_state.value.filter) }
            _state.update { it.copy(penalties = result) }
        }
    }
}
