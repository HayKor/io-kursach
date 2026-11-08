package ru.haykor.sudv.client.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.haykor.sudv.shared.domain.PenaltyStatus
import ru.haykor.sudv.shared.domain.ReportFormat
import ru.haykor.sudv.shared.domain.displayName

/** `Excel (XLSX)` / `CSV` in menus. */
val ReportFormat.label: String
    get() = when (this) {
        ReportFormat.XLSX -> "Excel (XLSX)"
        ReportFormat.CSV -> "CSV"
    }

/** A button that asks for the file format first; [busy] shows progress while the file is being made. */
@Composable
fun FormatButton(label: String, onSelect: (ReportFormat) -> Unit, modifier: Modifier = Modifier, busy: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, enabled = !busy) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text(label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReportFormat.entries.forEach { format ->
                DropdownMenuItem(text = { Text(format.label) }, onClick = {
                    expanded = false
                    onSelect(format)
                })
            }
        }
    }
}

/** An active penalty stands out; expired and removed ones no longer count, so they are muted. */
@Composable
fun StatusLabel(status: PenaltyStatus, modifier: Modifier = Modifier) {
    val color: Color = when (status) {
        PenaltyStatus.ACTIVE -> MaterialTheme.colorScheme.primary
        PenaltyStatus.EXPIRED, PenaltyStatus.REMOVED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(status.displayName, style = MaterialTheme.typography.labelLarge, color = color, modifier = modifier)
}
