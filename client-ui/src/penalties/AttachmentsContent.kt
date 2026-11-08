package ru.haykor.sudv.client.ui.penalties

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.jetbrains.compose.resources.painterResource
import ru.haykor.sudv.client.ui.common.ConfirmDialog
import ru.haykor.sudv.client.ui.common.FileAction
import ru.haykor.sudv.client.ui.common.deliverFile
import ru.haykor.sudv.client.ui.common.display
import ru.haykor.sudv.client.ui.common.fileSize
import ru.haykor.sudv.client.ui.common.rememberFileOutput
import ru.haykor.sudv.client.ui.common.rememberFilePicker
import ru.haykor.sudv.client.ui.resources.Res
import ru.haykor.sudv.client.ui.resources.ic_attach_file
import ru.haykor.sudv.client.ui.resources.ic_delete
import ru.haykor.sudv.client.ui.resources.ic_download
import ru.haykor.sudv.client.ui.resources.ic_image
import ru.haykor.sudv.client.ui.resources.ic_picture_as_pdf
import ru.haykor.sudv.shared.contract.AttachmentDto
import ru.haykor.sudv.shared.domain.AttachmentRules

@Composable
fun AttachmentsContent(component: AttachmentsComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
    val output = rememberFileOutput()
    val deliveryError = deliverFile(state.delivery, component::onDelivered)
    val pick = rememberFilePicker(AttachmentRules.contentTypesByExtension.keys, component::onFilePicked)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Вложения", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.attachments.isEmpty()) Text("Нет", style = MaterialTheme.typography.bodyLarge)
        state.attachments.forEach { attachment ->
            AttachmentRow(
                attachment = attachment,
                busy = attachment.id == state.busyId,
                canOpen = output.canOpen,
                canDelete = state.canEdit,
                onAction = { action -> component.onDownloadClick(attachment.id, action) },
                onDelete = { component.onDeleteClick(attachment.id) },
            )
        }
        (state.error ?: deliveryError.takeIf { state.delivery == null })?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.canEdit) {
            OutlinedButton(onClick = pick, enabled = !state.isUploading) {
                if (state.isUploading) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                } else {
                    Icon(painterResource(Res.drawable.ic_attach_file), contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text("Прикрепить файл", modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                "PDF или JPEG, не больше ${fileSize(AttachmentRules.MAX_BYTES)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    state.confirmingDelete?.let { attachment ->
        ConfirmDialog(
            title = "Удалить вложение?",
            text = "Файл «${attachment.fileName}» будет удалён без возможности восстановления.",
            confirmLabel = "Удалить",
            onConfirm = component::onDeleteConfirm,
            onDismiss = component::onDeleteDismiss,
        )
    }
}

@Composable
private fun AttachmentRow(
    attachment: AttachmentDto,
    busy: Boolean,
    canOpen: Boolean,
    canDelete: Boolean,
    onAction: (FileAction) -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        leadingContent = {
            val icon = if (attachment.contentType == "application/pdf") Res.drawable.ic_picture_as_pdf else Res.drawable.ic_image
            Icon(painterResource(icon), contentDescription = null)
        },
        headlineContent = { Text(attachment.fileName) },
        supportingContent = { Text("${fileSize(attachment.sizeBytes)} · ${attachment.uploadedAt.display()}") },
        trailingContent = {
            if (busy) {
                CircularProgressIndicator(Modifier.size(24.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onAction(FileAction.SAVE) }) {
                        Icon(painterResource(Res.drawable.ic_download), contentDescription = if (canOpen) "Сохранить" else "Скачать")
                    }
                    if (canDelete) {
                        IconButton(onClick = onDelete) {
                            Icon(painterResource(Res.drawable.ic_delete), contentDescription = "Удалить")
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        // Tapping the file opens it where the platform can, otherwise downloads it; the icons only save and delete,
        // so a phone keeps room for the name.
        modifier = Modifier.clickable(enabled = !busy) { onAction(if (canOpen) FileAction.OPEN else FileAction.SAVE) },
    )
}
