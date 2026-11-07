package ru.haykor.sudv.client.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ru.haykor.sudv.client.ui.common.LoadStateContent
import ru.haykor.sudv.client.ui.common.PaneHeader
import ru.haykor.sudv.client.ui.common.fullName
import ru.haykor.sudv.client.ui.resources.Res
import ru.haykor.sudv.client.ui.resources.ic_close
import ru.haykor.sudv.shared.contract.EmployeeDto
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.UserRole
import ru.haykor.sudv.shared.domain.displayName

@Composable
fun UserFormContent(component: UserFormComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
    Column(modifier.fillMaxSize()) {
        PaneHeader(
            title = "Новая учётная запись",
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
                EmployeePicker(employees, state.employeeId, component::onEmployeeChange, enabled)
                RolePicker(state.role, component::onRoleChange, enabled)
                OutlinedTextField(
                    value = state.login,
                    onValueChange = component::onLoginChange,
                    label = { Text("Логин") },
                    supportingText = { Text("Латинские буквы, цифры, точка, дефис, подчёркивание") },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = component::onPasswordChange,
                    label = { Text("Пароль") },
                    supportingText = { Text("Не короче 8 символов") },
                    singleLine = true,
                    enabled = enabled,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = component::onSave, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Создать")
                }
            }
        }
    }
}

/** Typing filters by name or personnel number; the list only has employees an account can be made for. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeePicker(
    employees: List<EmployeeDto>,
    selected: EmployeeId?,
    onSelect: (EmployeeId) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(employees.firstOrNull { it.id == selected }?.fullName.orEmpty()) }
    val matches = employees.filter {
        query.isBlank() || it.fullName.contains(query.trim(), ignoreCase = true) || it.personnelNumber.contains(query.trim(), ignoreCase = true)
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                expanded = true
            },
            label = { Text("Сотрудник") },
            supportingText = if (employees.isEmpty()) {
                { Text("У всех активных сотрудников уже есть учётные записи") }
            } else {
                null
            },
            singleLine = true,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded && matches.isNotEmpty(), onDismissRequest = { expanded = false }) {
            matches.forEach { employee ->
                DropdownMenuItem(
                    text = { Text("${employee.fullName} · № ${employee.personnelNumber} · ${employee.department.name}") },
                    onClick = {
                        query = employee.fullName
                        expanded = false
                        onSelect(employee.id)
                    },
                )
            }
        }
    }
}

/** Chips rather than segmented buttons: «Администратор» does not fit a third of a phone's width and wraps. */
@Composable
private fun RolePicker(selected: UserRole?, onSelect: (UserRole) -> Unit, enabled: Boolean) {
    Column {
        Text("Роль", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UserRole.entries.forEach { role ->
                FilterChip(
                    selected = role == selected,
                    onClick = { onSelect(role) },
                    enabled = enabled,
                    label = { Text(role.displayName) },
                )
            }
        }
    }
}
