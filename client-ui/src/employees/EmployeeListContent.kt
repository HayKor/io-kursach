package ru.haykor.sudv.client.ui.employees

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.jetbrains.compose.resources.painterResource
import ru.haykor.sudv.client.ui.common.DepartmentField
import ru.haykor.sudv.client.ui.common.LoadStateContent
import ru.haykor.sudv.client.ui.common.fullName
import ru.haykor.sudv.client.ui.resources.Res
import ru.haykor.sudv.client.ui.resources.ic_add
import ru.haykor.sudv.client.ui.resources.ic_refresh
import ru.haykor.sudv.client.ui.resources.ic_search
import ru.haykor.sudv.shared.contract.EmployeeDto
import ru.haykor.sudv.shared.domain.EmployeeId

@Composable
fun EmployeeListContent(component: EmployeeListComponent, selectedId: EmployeeId?, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = component::onQueryChange,
                label = { Text("ФИО или табельный номер") },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_search), contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.canFilterByDepartment) {
                    DepartmentField(
                        departments = state.departments,
                        selected = state.departmentId,
                        onSelect = component::onDepartmentChange,
                        label = "Подразделение",
                        allLabel = "Все",
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Box(Modifier.weight(1f))
                }
                FilterChip(
                    selected = state.includeArchived,
                    onClick = { component.onIncludeArchivedChange(!state.includeArchived) },
                    label = { Text("Архивные") },
                )
                IconButton(onClick = component::onRefresh) {
                    Icon(painterResource(Res.drawable.ic_refresh), contentDescription = "Обновить")
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LoadStateContent(state.employees, onRetry = component::onRefresh) { employees ->
                if (employees.isEmpty()) {
                    Text(
                        "Сотрудники не найдены",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    // Bottom padding keeps the last row clear of the «Добавить» button.
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                        items(employees, key = { it.id.value }) { employee ->
                            EmployeeRow(employee, selected = employee.id == selectedId) {
                                component.onEmployeeClick(employee.id)
                            }
                        }
                    }
                }
            }
            if (state.canCreate) {
                ExtendedFloatingActionButton(
                    onClick = component::onCreateClick,
                    icon = { Icon(painterResource(Res.drawable.ic_add), contentDescription = null) },
                    text = { Text("Добавить") },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun EmployeeRow(employee: EmployeeDto, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(employee.fullName) },
        supportingContent = {
            Text("№ ${employee.personnelNumber} · ${employee.position} · ${employee.department.name}")
        },
        trailingContent = if (employee.isArchived) {
            { Text("В архиве", style = MaterialTheme.typography.labelMedium) }
        } else {
            null
        },
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
