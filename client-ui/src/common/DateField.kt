package ru.haykor.sudv.client.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import ru.haykor.sudv.client.ui.resources.Res
import ru.haykor.sudv.client.ui.resources.ic_calendar_today
import ru.haykor.sudv.client.ui.resources.ic_close
import kotlin.time.Instant

/**
 * A read-only field that opens a date picker on tap. [onClear] turns the button of a filled field into «clear», for
 * optional dates such as filter bounds; [maxDate] disables later days, e.g. the future for an order date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    value: LocalDate?,
    onChange: (LocalDate) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxDate: LocalDate? = null,
    onClear: (() -> Unit)? = null,
) {
    var picking by remember { mutableStateOf(false) }
    // A read-only text field does not report clicks, only press interactions. Not collectIsPressedAsState: a quick tap
    // presses and releases within one frame, so the pressed state never shows `true`.
    val interactionSource = remember { MutableInteractionSource() }
    val isEnabled by rememberUpdatedState(enabled)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { if (it is PressInteraction.Release && isEnabled) picking = true }
    }

    OutlinedTextField(
        value = value?.display().orEmpty(),
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        // One button at a time: two would leave a filter field on a phone too little room for the date itself.
        trailingIcon = {
            if (onClear != null && value != null) {
                IconButton(onClick = onClear, enabled = enabled) {
                    Icon(painterResource(Res.drawable.ic_close), contentDescription = "Очистить")
                }
            } else {
                IconButton(onClick = { picking = true }, enabled = enabled) {
                    Icon(painterResource(Res.drawable.ic_calendar_today), contentDescription = "Выбрать дату")
                }
            }
        },
        interactionSource = interactionSource,
        modifier = modifier,
    )

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value?.toPickerMillis(),
            selectableDates = maxDate?.let { max ->
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= max.toPickerMillis()
                    override fun isSelectableYear(year: Int) = year <= max.year
                }
            } ?: object : SelectableDates {},
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { onChange(it.toPickerDate()) }
                        picking = false
                    },
                    enabled = state.selectedDateMillis != null,
                ) { Text("Выбрать") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Отмена") } },
        ) {
            DatePicker(state)
        }
    }
}

// The picker works with midnight UTC of the chosen day, whatever the device's time zone.
private fun LocalDate.toPickerMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toPickerDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
