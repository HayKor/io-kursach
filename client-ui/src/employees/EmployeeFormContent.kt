package ru.haykor.sudv.client.ui.employees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ru.haykor.sudv.client.ui.common.DepartmentField
import ru.haykor.sudv.client.ui.common.LoadStateContent
import ru.haykor.sudv.client.ui.common.PaneHeader
import ru.haykor.sudv.client.ui.resources.Res
import ru.haykor.sudv.client.ui.resources.ic_close

@Composable
fun EmployeeFormContent(component: EmployeeFormComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
    Column(modifier.fillMaxSize()) {
        PaneHeader(
            title = if (state.isNew) "Новый сотрудник" else "Изменение данных",
            navigationIcon = Res.drawable.ic_close,
            navigationDescription = "Отмена",
            onNavigate = component::onCancel,
        )
        LoadStateContent(state.prefill, onRetry = component::onRetry) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val enabled = !state.isSaving
                FormField("Табельный номер", state.personnelNumber, component::onPersonnelNumberChange, enabled)
                FormField("Фамилия", state.lastName, component::onLastNameChange, enabled)
                FormField("Имя", state.firstName, component::onFirstNameChange, enabled)
                FormField("Отчество (если есть)", state.middleName, component::onMiddleNameChange, enabled)
                FormField("Должность", state.position, component::onPositionChange, enabled)
                DepartmentField(
                    departments = state.departments,
                    selected = state.departmentId,
                    onSelect = { id -> id?.let(component::onDepartmentChange) },
                    label = "Подразделение",
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = component::onSave, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Сохранить")
                }
            }
        }
    }
}

@Composable
private fun FormField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}
