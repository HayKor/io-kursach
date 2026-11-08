package ru.haykor.sudv.client.ui.penalties

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ru.haykor.sudv.client.ui.common.DateField
import ru.haykor.sudv.client.ui.common.EmployeePicker
import ru.haykor.sudv.client.ui.common.LoadStateContent
import ru.haykor.sudv.client.ui.common.PaneHeader
import ru.haykor.sudv.client.ui.common.display
import ru.haykor.sudv.client.ui.resources.Res
import ru.haykor.sudv.client.ui.resources.ic_close
import ru.haykor.sudv.shared.domain.PenaltyType
import ru.haykor.sudv.shared.domain.displayName

@Composable
fun PenaltyFormContent(component: PenaltyFormComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
    Column(modifier.fillMaxSize()) {
        PaneHeader(
            title = "Новое взыскание",
            navigationIcon = Res.drawable.ic_close,
            navigationDescription = "Отмена",
            onNavigate = component::onCancel,
        )
        LoadStateContent(state.employees, onRetry = component::onRetry) { employees ->
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val enabled = !state.isSaving
                EmployeePicker(
                    employees = employees,
                    selected = state.employeeId,
                    onSelect = component::onEmployeeChange,
                    enabled = enabled,
                    emptyText = "Нет активных сотрудников",
                )
                Column {
                    Text("Вид взыскания", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PenaltyType.entries.forEach { type ->
                            FilterChip(
                                selected = type == state.type,
                                onClick = { component.onTypeChange(type) },
                                enabled = enabled,
                                label = { Text(type.displayName) },
                            )
                        }
                    }
                }
                DateField(
                    value = state.violationDate,
                    onChange = component::onViolationDateChange,
                    label = "Дата нарушения",
                    enabled = enabled,
                    maxDate = state.today,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.description,
                    onValueChange = component::onDescriptionChange,
                    label = { Text("Описание нарушения") },
                    minLines = 3,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.orderNumber,
                    onValueChange = component::onOrderNumberChange,
                    label = { Text("Номер приказа") },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column {
                    DateField(
                        value = state.orderDate,
                        onChange = component::onOrderDateChange,
                        label = "Дата приказа",
                        enabled = enabled,
                        maxDate = state.today,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.expiryDate?.let {
                        Text(
                            "Погасится ${it.display()}, через год после приказа",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                        )
                    }
                }
                state.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = component::onSave, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Зарегистрировать")
                }
            }
        }
    }
}
