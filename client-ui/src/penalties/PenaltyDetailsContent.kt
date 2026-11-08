package ru.haykor.sudv.client.ui.penalties

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.datetime.LocalDate
import ru.haykor.sudv.client.ui.common.DateField
import ru.haykor.sudv.client.ui.common.LoadState
import ru.haykor.sudv.client.ui.common.LoadStateContent
import ru.haykor.sudv.client.ui.common.PaneHeader
import ru.haykor.sudv.client.ui.common.StatusLabel
import ru.haykor.sudv.client.ui.common.days
import ru.haykor.sudv.client.ui.common.display
import ru.haykor.sudv.client.ui.common.fullName
import ru.haykor.sudv.client.ui.resources.Res
import ru.haykor.sudv.client.ui.resources.ic_arrow_back
import ru.haykor.sudv.client.ui.resources.ic_close
import ru.haykor.sudv.shared.contract.PenaltyDto
import ru.haykor.sudv.shared.contract.StatusEventDto
import ru.haykor.sudv.shared.domain.PenaltyStatus
import ru.haykor.sudv.shared.domain.displayName

/** [twoPane]: the registry is visible next to the card, so the card closes instead of going back. */
@Composable
fun PenaltyDetailsContent(component: PenaltyDetailsComponent, twoPane: Boolean, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
    val penalty = (state.details as? LoadState.Loaded)?.data?.penalty
    Column(modifier.fillMaxSize()) {
        PaneHeader(
            title = penalty?.type?.displayName ?: "Взыскание",
            navigationIcon = if (twoPane) Res.drawable.ic_close else Res.drawable.ic_arrow_back,
            navigationDescription = if (twoPane) "Закрыть" else "Назад",
            onNavigate = component::onCloseClick,
        )
        LoadStateContent(state.details, onRetry = component::onRetry) { details ->
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val shown = details.penalty
                Field("Сотрудник", shown.employee.fullName)
                Field("Табельный номер и подразделение", "№ ${shown.employee.personnelNumber} · ${shown.employee.department.name}")
                Column {
                    Label("Статус")
                    StatusLabel(shown.status)
                }
                Expiry(shown, state.remainingDays)
                Field("Дата нарушения", shown.violationDate.display())
                Field("Описание", shown.description)
                Field("Приказ", order(shown.orderNumber, shown.orderDate))
                shown.removal?.let { Field("Приказ о досрочном снятии", order(it.orderNumber, it.orderDate)) }
                if (state.canRemove) {
                    OutlinedButton(onClick = component::onRemoveClick) { Text("Снять досрочно") }
                }
                AttachmentsContent(component.attachments)
                History(details.events)
            }
        }
    }
    state.removal?.let { RemovalDialog(it, state.today, component) }
}

@Composable
private fun Expiry(penalty: PenaltyDto, remainingDays: Int?) {
    val date = penalty.expiryDate.display()
    val text = when (penalty.status) {
        // The date has come, but the nightly scheduler has not marked the penalty expired yet.
        PenaltyStatus.ACTIVE if remainingDays == 0 -> "$date, срок истёк: статус обновится при ежедневной проверке"
        PenaltyStatus.ACTIVE -> "$date, осталось ${days(remainingDays ?: 0)}"
        PenaltyStatus.EXPIRED -> "$date, погашено"
        PenaltyStatus.REMOVED -> "$date, не применяется: снято досрочно"
    }
    Field("Дата погашения", text)
}

@Composable
private fun History(events: List<StatusEventDto>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label("История статусов")
        events.sortedBy { it.changedAt }.forEach { event ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(event.changedAt.display(), style = MaterialTheme.typography.bodyMedium)
                Text(event.status.displayName, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun RemovalDialog(form: PenaltyDetailsComponent.RemovalForm, today: LocalDate, component: PenaltyDetailsComponent) {
    val enabled = !form.isSaving
    AlertDialog(
        onDismissRequest = component::onRemovalDismiss,
        title = { Text("Снять досрочно") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Укажите приказ о снятии. Отменить снятие нельзя.")
                OutlinedTextField(
                    value = form.orderNumber,
                    onValueChange = component::onRemovalOrderNumberChange,
                    label = { Text("Номер приказа") },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(
                    value = form.orderDate,
                    onChange = component::onRemovalOrderDateChange,
                    label = "Дата приказа",
                    enabled = enabled,
                    maxDate = today,
                    modifier = Modifier.fillMaxWidth(),
                )
                form.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = component::onRemovalConfirm, enabled = enabled) {
                if (form.isSaving) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Снять")
            }
        },
        dismissButton = { TextButton(onClick = component::onRemovalDismiss, enabled = enabled) { Text("Отмена") } },
    )
}

private fun order(number: String, date: LocalDate) = "№ $number от ${date.display()}"

@Composable
private fun Field(label: String, value: String) {
    Column {
        Label(label)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
