package ru.haykor.sudv.client.ui.employees

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ru.haykor.sudv.client.ui.common.ListDetailLayout

@Composable
fun EmployeesContent(component: EmployeesComponent, modifier: Modifier = Modifier) {
    val stack by component.stack.subscribeAsState()
    val list = stack.items.first().instance as EmployeesComponent.Child.List
    val selectedId = when (val active = stack.active.instance) {
        is EmployeesComponent.Child.Details -> active.component.employeeId
        is EmployeesComponent.Child.Form -> active.component.employeeId
        is EmployeesComponent.Child.List -> null
    }
    ListDetailLayout(
        stack = stack,
        placeholder = "Выберите сотрудника",
        list = { listModifier -> EmployeeListContent(list.component, selectedId, listModifier) },
        detail = { child, twoPane ->
            when (child) {
                is EmployeesComponent.Child.Details -> EmployeeDetailsContent(child.component, twoPane)
                is EmployeesComponent.Child.Form -> EmployeeFormContent(child.component)
                is EmployeesComponent.Child.List -> Unit
            }
        },
        modifier = modifier,
    )
}
