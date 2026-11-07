package ru.haykor.sudv.client.ui.employees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.jetbrains.compose.resources.painterResource
import ru.haykor.sudv.client.ui.common.ConfirmDialog
import ru.haykor.sudv.client.ui.common.LoadState
import ru.haykor.sudv.client.ui.common.LoadStateContent
import ru.haykor.sudv.client.ui.common.PaneHeader
import ru.haykor.sudv.client.ui.common.fullName
import ru.haykor.sudv.client.ui.resources.Res
import ru.haykor.sudv.client.ui.resources.ic_arrow_back
import ru.haykor.sudv.client.ui.resources.ic_close
import ru.haykor.sudv.client.ui.resources.ic_edit
import ru.haykor.sudv.client.ui.resources.ic_inventory_2

/** [twoPane]: the list is visible next to the card, so the card closes instead of going back. */
@Composable
fun EmployeeDetailsContent(component: EmployeeDetailsComponent, twoPane: Boolean, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
    val employee = (state.employee as? LoadState.Loaded)?.data
    Column(modifier.fillMaxSize()) {
        PaneHeader(
            title = employee?.fullName ?: "Сотрудник",
            navigationIcon = if (twoPane) Res.drawable.ic_close else Res.drawable.ic_arrow_back,
            navigationDescription = if (twoPane) "Закрыть" else "Назад",
            onNavigate = component::onCloseClick,
        ) {
            if (state.isArchiving) CircularProgressIndicator(Modifier.padding(12.dp))
            if (state.canEdit && employee != null) {
                IconButton(onClick = component::onEditClick) {
                    Icon(painterResource(Res.drawable.ic_edit), contentDescription = "Изменить")
                }
            }
            if (state.canArchive && !state.isArchiving) {
                IconButton(onClick = component::onArchiveClick) {
                    Icon(painterResource(Res.drawable.ic_inventory_2), contentDescription = "В архив")
                }
            }
        }
        LoadStateContent(state.employee, onRetry = component::onRetry) { loaded ->
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (loaded.isArchived) {
                    Text(
                        "В архиве: не показывается в списках, история взысканий сохранена",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Field("Табельный номер", loaded.personnelNumber)
                Field("Должность", loaded.position)
                Field("Подразделение", loaded.department.name)
            }
        }
    }
    if (state.confirmingArchive) {
        ConfirmDialog(
            title = "Перевести в архив?",
            text = "Сотрудник пропадёт из списков, его взыскания сохранятся. Вернуть из архива нельзя.",
            confirmLabel = "В архив",
            onConfirm = component::onArchiveConfirm,
            onDismiss = component::onArchiveDismiss,
        )
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
