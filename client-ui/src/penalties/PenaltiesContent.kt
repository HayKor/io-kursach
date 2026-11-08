package ru.haykor.sudv.client.ui.penalties

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ru.haykor.sudv.client.ui.common.ListDetailLayout

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
