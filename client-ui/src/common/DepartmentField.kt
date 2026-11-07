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
import ru.haykor.sudv.shared.contract.DepartmentDto
import ru.haykor.sudv.shared.domain.DepartmentId

/** Department picker; [allLabel] adds a «no department» option for filters, e.g. «Все». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepartmentField(
    departments: List<DepartmentDto>,
    selected: DepartmentId?,
    onSelect: (DepartmentId?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    allLabel: String? = null,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val text = departments.firstOrNull { it.id == selected }?.name ?: allLabel.orEmpty()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allLabel != null) {
                DropdownMenuItem(text = { Text(allLabel) }, onClick = {
                    expanded = false
                    onSelect(null)
                })
            }
            departments.forEach { department ->
                DropdownMenuItem(text = { Text(department.name) }, onClick = {
                    expanded = false
                    onSelect(department.id)
                })
            }
        }
    }
}
