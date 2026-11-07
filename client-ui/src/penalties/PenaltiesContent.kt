package ru.haykor.sudv.client.ui.penalties

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ru.haykor.sudv.client.ui.common.ListDetailLayout
import ru.haykor.sudv.shared.domain.PenaltyStatus
import ru.haykor.sudv.shared.domain.displayName

@Composable
fun PenaltiesContent(component: PenaltiesComponent, modifier: Modifier = Modifier) {
    val stack by component.stack.subscribeAsState()
    val list = stack.items.first().instance as PenaltiesComponent.Child.List
    val selectedId = (stack.active.instance as? PenaltiesComponent.Child.Details)?.component?.penaltyId
    ListDetailLayout(
        stack = stack,
        placeholder = "Выберите взыскание",
        list = { listModifier -> PenaltyListContent(list.component, selectedId, listModifier) },
        detail = { child, twoPane ->
            when (child) {
                is PenaltiesComponent.Child.Details -> PenaltyDetailsContent(child.component, twoPane)
                is PenaltiesComponent.Child.Form -> PenaltyFormContent(child.component)
                is PenaltiesComponent.Child.List -> Unit
            }
        },
        modifier = modifier,
    )
}

/** An active penalty stands out; expired and removed ones no longer count, so they are muted. */
@Composable
internal fun StatusLabel(status: PenaltyStatus, modifier: Modifier = Modifier) {
    val color: Color = when (status) {
        PenaltyStatus.ACTIVE -> MaterialTheme.colorScheme.primary
        PenaltyStatus.EXPIRED, PenaltyStatus.REMOVED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(status.displayName, style = MaterialTheme.typography.labelLarge, color = color, modifier = modifier)
}
