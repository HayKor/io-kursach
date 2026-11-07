package ru.haykor.sudv.client.ui.penalties

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.jetbrains.compose.resources.painterResource
import ru.haykor.sudv.client.ui.common.DateField
import ru.haykor.sudv.client.ui.common.DepartmentField
import ru.haykor.sudv.client.ui.common.LoadStateContent
import ru.haykor.sudv.client.ui.common.display
import ru.haykor.sudv.client.ui.common.fullName
import ru.haykor.sudv.client.ui.resources.Res
import ru.haykor.sudv.client.ui.resources.ic_add
import ru.haykor.sudv.client.ui.resources.ic_arrow_downward
import ru.haykor.sudv.client.ui.resources.ic_arrow_upward
import ru.haykor.sudv.client.ui.resources.ic_filter_list
import ru.haykor.sudv.client.ui.resources.ic_refresh
import ru.haykor.sudv.client.ui.resources.ic_search
import ru.haykor.sudv.shared.contract.PenaltyDto
import ru.haykor.sudv.shared.contract.PenaltySortField
import ru.haykor.sudv.shared.contract.SortDirection
import ru.haykor.sudv.shared.domain.PenaltyId
import ru.haykor.sudv.shared.domain.PenaltyStatus
import ru.haykor.sudv.shared.domain.displayName

@Composable
fun PenaltyListContent(component: PenaltyListComponent, selectedId: PenaltyId?, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.employeeName,
                onValueChange = component::onEmployeeNameChange,
                label = { Text("ФИО сотрудника") },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_search), contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = component::onFiltersToggle) {
                BadgedBox(badge = { if (state.activeFilterCount > 0) Badge { Text("${state.activeFilterCount}") } }) {
                    Icon(
                        painterResource(Res.drawable.ic_filter_list),
                        contentDescription = if (state.filtersShown) "Скрыть фильтры" else "Фильтры",
                    )
                }
            }
            IconButton(onClick = component::onRefresh) {
                Icon(painterResource(Res.drawable.ic_refresh), contentDescription = "Обновить")
            }
        }
        if (state.filtersShown) {
            Filters(state, component)
            HorizontalDivider()
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LoadStateContent(state.penalties, onRetry = component::onRefresh) { penalties ->
                if (penalties.isEmpty()) {
                    Text(
                        "Взыскания не найдены",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    // Bottom padding keeps the last row clear of the «Зарегистрировать» button.
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                        items(penalties, key = { it.id.value }) { penalty ->
                            PenaltyRow(penalty, selected = penalty.id == selectedId) { component.onPenaltyClick(penalty.id) }
                        }
                    }
                }
            }
            if (state.canCreate) {
                ExtendedFloatingActionButton(
                    onClick = component::onCreateClick,
                    icon = { Icon(painterResource(Res.drawable.ic_add), contentDescription = null) },
                    text = { Text("Зарегистрировать") },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun Filters(state: PenaltyListComponent.State, component: PenaltyListComponent) {
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.status == null, onClick = { component.onStatusChange(null) }, label = { Text("Все") })
            PenaltyStatus.entries.forEach { status ->
                FilterChip(
                    selected = state.status == status,
                    onClick = { component.onStatusChange(status) },
                    label = { Text(status.displayName) },
                )
            }
        }
        if (state.canFilterByDepartment) {
            DepartmentField(
                departments = state.departments,
                selected = state.departmentId,
                onSelect = component::onDepartmentChange,
                label = "Подразделение",
                allLabel = "Все",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField(
                value = state.orderDateFrom,
                onChange = component::onOrderDateFromChange,
                onClear = { component.onOrderDateFromChange(null) },
                label = "Приказ с",
                modifier = Modifier.weight(1f),
            )
            DateField(
                value = state.orderDateTo,
                onChange = component::onOrderDateToChange,
                onClear = { component.onOrderDateToChange(null) },
                label = "по",
                modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SortField(state.sort, component::onSortChange, Modifier.weight(1f))
            IconButton(onClick = component::onDirectionToggle) {
                if (state.direction == SortDirection.ASC) {
                    Icon(painterResource(Res.drawable.ic_arrow_upward), contentDescription = "По возрастанию")
                } else {
                    Icon(painterResource(Res.drawable.ic_arrow_downward), contentDescription = "По убыванию")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortField(selected: PenaltySortField, onSelect: (PenaltySortField) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Сортировка") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PenaltySortField.entries.forEach { field ->
                DropdownMenuItem(text = { Text(field.label) }, onClick = {
                    expanded = false
                    onSelect(field)
                })
            }
        }
    }
}

private val PenaltySortField.label: String
    get() = when (this) {
        PenaltySortField.EMPLOYEE_NAME -> "ФИО"
        PenaltySortField.VIOLATION_DATE -> "Дата нарушения"
        PenaltySortField.ORDER_DATE -> "Дата приказа"
        PenaltySortField.STATUS -> "Статус"
    }

@Composable
private fun PenaltyRow(penalty: PenaltyDto, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(penalty.employee.fullName) },
        supportingContent = {
            Column {
                Text("${penalty.type.displayName} · приказ № ${penalty.orderNumber} от ${penalty.orderDate.display()}")
                Text(penalty.employee.department.name)
            }
        },
        trailingContent = { StatusLabel(penalty.status) },
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
