package ru.haykor.sudv.client.ui.users

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ru.haykor.sudv.client.ui.common.ListDetailLayout

@Composable
fun UsersContent(component: UsersComponent, modifier: Modifier = Modifier) {
    val stack by component.stack.subscribeAsState()
    val list = stack.items.first().instance as UsersComponent.Child.List
    ListDetailLayout(
        stack = stack,
        placeholder = "Новая учётная запись — кнопка «Добавить»",
        list = { listModifier -> UserListContent(list.component, listModifier) },
        detail = { child, _ ->
            when (child) {
                is UsersComponent.Child.Form -> UserFormContent(child.component)
                is UsersComponent.Child.List -> Unit
            }
        },
        modifier = modifier,
    )
}
