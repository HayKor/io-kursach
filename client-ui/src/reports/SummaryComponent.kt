package ru.haykor.sudv.client.ui.reports

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
import ru.haykor.sudv.client.ui.common.FileAction
import ru.haykor.sudv.client.ui.common.FileDelivery
import ru.haykor.sudv.client.ui.common.LoadState
import ru.haykor.sudv.client.ui.common.load
import ru.haykor.sudv.shared.contract.CreateReportRequest
import ru.haykor.sudv.shared.contract.DepartmentDto
import ru.haykor.sudv.shared.contract.ReportDto
import ru.haykor.sudv.shared.contract.SummaryReportDto
import ru.haykor.sudv.shared.domain.DepartmentId
import ru.haykor.sudv.shared.domain.ReportFormat
import ru.haykor.sudv.shared.domain.Validation
import kotlin.time.Clock

/** The summary report: penalties by department for a period, shown on screen and saved to the archive as a file. */
interface SummaryComponent {
    val state: Value<State>

    fun onPeriodFromChange(date: LocalDate?)
    fun onPeriodToChange(date: LocalDate?)
    fun onDepartmentToggle(id: DepartmentId)
    fun onAllDepartmentsClick()
    fun onBuild()

    /** Saves the shown report, with exactly its parameters, to the archive. */
    fun onSave(format: ReportFormat)
    fun onDownloadSaved()
    fun onDelivered()
    fun onArchiveClick()
    fun onRetry()

    data class State(
        val periodFrom: LocalDate?,
        val periodTo: LocalDate?,
        val departments: LoadState<List<DepartmentDto>> = LoadState.Loading,
        /** Empty means all departments, as on the server. */
        val selectedDepartmentIds: Set<DepartmentId> = emptySet(),
        val errors: List<String> = emptyList(),
        val isBuilding: Boolean = false,
        /** The built report; any change of the parameters clears it, so what is saved is always what is shown. */
        val report: ShownReport? = null,
        val isSaving: Boolean = false,
        /** The archive entry made from [report]. */
        val saved: ReportDto? = null,
        val isDownloading: Boolean = false,
        val delivery: FileDelivery? = null,
    )

    /** [departmentIds] as requested: empty for «all», so the archive entry covers the same departments. */
    data class ShownReport(val summary: SummaryReportDto, val departmentIds: List<DepartmentId>)
}

class DefaultSummaryComponent(
    componentContext: ComponentContext,
    private val apiClient: IApiClient,
    private val onArchive: () -> Unit,
    clock: Clock = Clock.System,
) : SummaryComponent, ComponentContext by componentContext {
    private val scope = coroutineScope()

    // The current year so far: the usual period for a yearly summary.
    private val _state = MutableValue(
        clock.todayIn(TimeZone.currentSystemDefault()).let { today ->
            SummaryComponent.State(periodFrom = LocalDate(today.year, 1, 1), periodTo = today)
        },
    )
    override val state: Value<SummaryComponent.State> = _state

    init {
        loadDepartments()
    }

    override fun onPeriodFromChange(date: LocalDate?) = edit { copy(periodFrom = date) }
    override fun onPeriodToChange(date: LocalDate?) = edit { copy(periodTo = date) }

    override fun onDepartmentToggle(id: DepartmentId) = edit {
        copy(selectedDepartmentIds = if (id in selectedDepartmentIds) selectedDepartmentIds - id else selectedDepartmentIds + id)
    }

    override fun onAllDepartmentsClick() = edit { copy(selectedDepartmentIds = emptySet()) }

    override fun onBuild() {
        val form = _state.value
        if (form.isBuilding) return
        val from = form.periodFrom
        val to = form.periodTo
        val errors = if (from == null || to == null) listOf("Укажите начало и конец периода") else Validation.period(from, to)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        // Sorted for a stable request: the set has no order of its own.
        val departmentIds = form.selectedDepartmentIds.sortedBy { it.value }
        _state.update { it.copy(isBuilding = true, errors = emptyList()) }
        scope.launch {
            try {
                val summary = apiClient.summaryReport(from!!, to!!, departmentIds)
                _state.update { it.copy(isBuilding = false, report = SummaryComponent.ShownReport(summary, departmentIds)) }
            } catch (e: ApiClientException) {
                _state.update { it.copy(isBuilding = false, errors = listOf(e.message)) }
            }
        }
    }

    override fun onSave(format: ReportFormat) {
        val shown = _state.value
        val report = shown.report ?: return
        if (shown.isSaving) return
        _state.update { it.copy(isSaving = true, errors = emptyList()) }
        scope.launch {
            try {
                val request = CreateReportRequest(report.summary.periodFrom, report.summary.periodTo, report.departmentIds, format)
                val saved = apiClient.createReport(request)
                _state.update { it.copy(isSaving = false, saved = saved) }
            } catch (e: ApiClientException) {
                _state.update { it.copy(isSaving = false, errors = listOf(e.message)) }
            }
        }
    }

    override fun onDownloadSaved() {
        val saved = _state.value.saved ?: return
        if (_state.value.isDownloading) return
        _state.update { it.copy(isDownloading = true, errors = emptyList()) }
        scope.launch {
            try {
                val file = apiClient.downloadReport(saved.id)
                _state.update { it.copy(isDownloading = false, delivery = FileDelivery(file, FileAction.SAVE)) }
            } catch (e: ApiClientException) {
                _state.update { it.copy(isDownloading = false, errors = listOf(e.message)) }
            }
        }
    }

    override fun onDelivered() = _state.update { it.copy(delivery = null) }

    override fun onArchiveClick() = onArchive()

    override fun onRetry() = loadDepartments()

    private fun loadDepartments() {
        scope.launch {
            _state.update { it.copy(departments = LoadState.Loading) }
            val result = load { apiClient.departments() }
            _state.update { it.copy(departments = result) }
        }
    }

    // A changed parameter makes the shown report (and its archive entry) describe something else, so both go.
    private fun edit(change: SummaryComponent.State.() -> SummaryComponent.State) =
        _state.update { it.change().copy(errors = emptyList(), report = null, saved = null) }
}
