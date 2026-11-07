package ru.haykor.sudv.client.ui.penalties

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ru.haykor.sudv.client.domain.ApiClientException
import ru.haykor.sudv.client.domain.FileContent
import ru.haykor.sudv.client.domain.IApiClient
import ru.haykor.sudv.client.ui.common.FileAction
import ru.haykor.sudv.client.ui.common.FileDelivery
import ru.haykor.sudv.client.ui.common.PickedFile
import ru.haykor.sudv.client.ui.common.fileSize
import ru.haykor.sudv.shared.contract.AttachmentDto
import ru.haykor.sudv.shared.domain.AttachmentId
import ru.haykor.sudv.shared.domain.AttachmentRules
import ru.haykor.sudv.shared.domain.PenaltyId

/** A penalty's attachments: HR uploads and deletes them, HR and MANAGER download them. */
interface AttachmentsComponent {
    val state: Value<State>

    fun onFilePicked(file: PickedFile)
    fun onDownloadClick(id: AttachmentId, action: FileAction)

    /** The UI has handed [State.delivery] to the platform (saved, opened or cancelled by the user). */
    fun onDelivered()
    fun onDeleteClick(id: AttachmentId)
    fun onDeleteConfirm()
    fun onDeleteDismiss()

    data class State(
        val attachments: List<AttachmentDto> = emptyList(),
        val canEdit: Boolean = false,
        val isUploading: Boolean = false,
        /** The attachment being downloaded or deleted; its row shows progress instead of actions. */
        val busyId: AttachmentId? = null,
        val confirmingDelete: AttachmentDto? = null,
        val error: String? = null,
        /** A downloaded file waiting for the platform to save or open it. */
        val delivery: FileDelivery? = null,
    )
}

class DefaultAttachmentsComponent(
    componentContext: ComponentContext,
    private val apiClient: IApiClient,
    private val penaltyId: PenaltyId,
    canEdit: Boolean,
) : AttachmentsComponent, ComponentContext by componentContext {
    private val scope = coroutineScope()
    private val _state = MutableValue(AttachmentsComponent.State(canEdit = canEdit))
    override val state: Value<AttachmentsComponent.State> = _state

    /** Called by the card whenever it (re)loads the penalty. */
    fun setAttachments(attachments: List<AttachmentDto>) = _state.update { it.copy(attachments = attachments) }

    override fun onFilePicked(file: PickedFile) {
        val shown = _state.value
        if (!shown.canEdit || shown.isUploading) return
        // The same rules as the server's, checked first so a wrong or huge file is not sent at all.
        val contentType = AttachmentRules.contentTypeOf(file.name)
        val error = when {
            contentType == null -> UNSUPPORTED
            file.size == 0L -> "Файл пустой"
            file.size > AttachmentRules.MAX_BYTES -> "Файл больше ${fileSize(AttachmentRules.MAX_BYTES)}"
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        _state.update { it.copy(isUploading = true, error = null) }
        scope.launch {
            try {
                val bytes = file.read()
                // The extension can lie: a PNG renamed to .jpg is caught by its first bytes.
                if (!AttachmentRules.matchesSignature(contentType!!, bytes)) {
                    _state.update { it.copy(isUploading = false, error = UNSUPPORTED) }
                    return@launch
                }
                val uploaded = apiClient.uploadAttachment(penaltyId, FileContent(file.name, contentType, bytes))
                _state.update { it.copy(attachments = it.attachments + uploaded, isUploading = false) }
            } catch (e: ApiClientException) {
                _state.update { it.copy(isUploading = false, error = e.message) }
            }
        }
    }

    override fun onDownloadClick(id: AttachmentId, action: FileAction) {
        if (_state.value.busyId != null) return
        _state.update { it.copy(busyId = id, error = null) }
        scope.launch {
            try {
                val file = apiClient.downloadAttachment(id)
                _state.update { it.copy(busyId = null, delivery = FileDelivery(file, action)) }
            } catch (e: ApiClientException) {
                _state.update { it.copy(busyId = null, error = e.message) }
            }
        }
    }

    override fun onDelivered() = _state.update { it.copy(delivery = null) }

    override fun onDeleteClick(id: AttachmentId) {
        val attachment = _state.value.attachments.firstOrNull { it.id == id } ?: return
        if (_state.value.canEdit) _state.update { it.copy(confirmingDelete = attachment, error = null) }
    }

    override fun onDeleteDismiss() = _state.update { it.copy(confirmingDelete = null) }

    override fun onDeleteConfirm() {
        val attachment = _state.value.confirmingDelete ?: return
        _state.update { it.copy(confirmingDelete = null, busyId = attachment.id) }
        scope.launch {
            try {
                apiClient.deleteAttachment(attachment.id)
                _state.update { state -> state.copy(attachments = state.attachments - attachment, busyId = null) }
            } catch (e: ApiClientException) {
                _state.update { it.copy(busyId = null, error = e.message) }
            }
        }
    }

    private companion object {
        const val UNSUPPORTED = "Допустимы только файлы PDF и JPEG"
    }
}
