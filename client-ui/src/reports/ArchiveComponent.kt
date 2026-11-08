package ru.haykor.sudv.client.ui.reports

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ru.haykor.sudv.client.domain.ApiClientException
import ru.haykor.sudv.client.domain.IApiClient
import ru.haykor.sudv.client.ui.common.FileAction
import ru.haykor.sudv.client.ui.common.FileDelivery
import ru.haykor.sudv.client.ui.common.LoadState
import ru.haykor.sudv.client.ui.common.load
import ru.haykor.sudv.shared.contract.ReportDto
import ru.haykor.sudv.shared.domain.ReportId

/** Saved summary reports, newest first; each can be downloaded again exactly as it was made. */
interface ArchiveComponent {
    val state: Value<State>

    fun onDownloadClick(id: ReportId)
    fun onDelivered()
    fun onRefresh()
    fun onCloseClick()

    data class State(
        val reports: LoadState<List<ReportDto>> = LoadState.Loading,
        val busyId: ReportId? = null,
        val error: String? = null,
        val delivery: FileDelivery? = null,
    )
}

class DefaultArchiveComponent(
    componentContext: ComponentContext,
    private val apiClient: IApiClient,
    private val onClose: () -> Unit,
) : ArchiveComponent, ComponentContext by componentContext {
    private val scope = coroutineScope()
    private val _state = MutableValue(ArchiveComponent.State())
    override val state: Value<ArchiveComponent.State> = _state

    init {
        onRefresh()
    }

    override fun onDownloadClick(id: ReportId) {
        if (_state.value.busyId != null) return
        _state.update { it.copy(busyId = id, error = null) }
        scope.launch {
            try {
                val file = apiClient.downloadReport(id)
                _state.update { it.copy(busyId = null, delivery = FileDelivery(file, FileAction.SAVE)) }
            } catch (e: ApiClientException) {
                _state.update { it.copy(busyId = null, error = e.message) }
            }
        }
    }

    override fun onDelivered() = _state.update { it.copy(delivery = null) }

    override fun onRefresh() {
        scope.launch {
            if (_state.value.reports !is LoadState.Loaded) _state.update { it.copy(reports = LoadState.Loading) }
            val result = load { apiClient.reports().sortedByDescending { it.createdAt } }
            _state.update { it.copy(reports = result) }
        }
    }

    override fun onCloseClick() = onClose()
}
