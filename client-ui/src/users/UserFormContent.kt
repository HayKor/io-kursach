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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ru.haykor.sudv.client.ui.common.EmployeePicker
import ru.haykor.sudv.client.ui.common.LoadStateContent
import ru.haykor.sudv.client.ui.common.PaneHeader
import ru.haykor.sudv.client.ui.resources.Res
import ru.haykor.sudv.client.ui.resources.ic_close
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
                EmployeePicker(
                    employees = employees,
                    selected = state.employeeId,
                    onSelect = component::onEmployeeChange,
                    enabled = enabled,
                    emptyText = "У всех активных сотрудников уже есть учётные записи",
                )
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
