package ru.haykor.sudv.client.ui.employees

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState

/** From this width (Material's «expanded» window class) the list stays visible next to the open card or form. */
private val TwoPaneMinWidth = 840.dp

@Composable
fun EmployeesContent(component: EmployeesComponent, modifier: Modifier = Modifier) {
    val stack by component.stack.subscribeAsState()
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= TwoPaneMinWidth) {
            // The list is always the bottom of the stack; the right pane shows whatever is on top of it.
            val list = stack.items.first().instance as EmployeesComponent.Child.List
            val active = stack.active.instance
            Row(Modifier.fillMaxSize()) {
                EmployeeListContent(
                    component = list.component,
                    selectedId = when (active) {
                        is EmployeesComponent.Child.Details -> active.component.employeeId
                        is EmployeesComponent.Child.Form -> active.component.employeeId
                        is EmployeesComponent.Child.List -> null
                    },
                    modifier = Modifier.width(400.dp).fillMaxHeight(),
                )
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (active is EmployeesComponent.Child.List) {
                        Text(
                            "Выберите сотрудника",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        ChildContent(active, twoPane = true)
                    }
                }
            }
        } else {
            Children(stack = stack) { ChildContent(it.instance, twoPane = false) }
        }
    }
}

@Composable
private fun ChildContent(child: EmployeesComponent.Child, twoPane: Boolean) {
    when (child) {
        is EmployeesComponent.Child.List -> EmployeeListContent(child.component, selectedId = null)
        is EmployeesComponent.Child.Details -> EmployeeDetailsContent(child.component, twoPane)
        is EmployeesComponent.Child.Form -> EmployeeFormContent(child.component)
    }
}
