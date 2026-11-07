package ru.haykor.sudv.client.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.haykor.sudv.shared.contract.EmployeeDto
import ru.haykor.sudv.shared.domain.EmployeeId

/** Typing filters by name or personnel number; [emptyText] explains why there is nobody to pick. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeePicker(
    employees: List<EmployeeDto>,
    selected: EmployeeId?,
    onSelect: (EmployeeId) -> Unit,
    enabled: Boolean,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(employees.firstOrNull { it.id == selected }?.fullName.orEmpty()) }
    val matches = employees.filter {
        query.isBlank() || it.fullName.contains(query.trim(), ignoreCase = true) || it.personnelNumber.contains(query.trim(), ignoreCase = true)
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                expanded = true
            },
            label = { Text("Сотрудник") },
            supportingText = if (employees.isEmpty()) {
                { Text(emptyText) }
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
