package ru.haykor.sudv.client.ui.users

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.jetbrains.compose.resources.painterResource
import ru.haykor.sudv.client.ui.common.ConfirmDialog
import ru.haykor.sudv.client.ui.common.LoadStateContent
import ru.haykor.sudv.client.ui.common.fullName
import ru.haykor.sudv.client.ui.resources.Res
import ru.haykor.sudv.client.ui.resources.ic_add
import ru.haykor.sudv.client.ui.resources.ic_lock
import ru.haykor.sudv.client.ui.resources.ic_lock_open
import ru.haykor.sudv.client.ui.resources.ic_refresh
import ru.haykor.sudv.shared.contract.UserDto
import ru.haykor.sudv.shared.domain.displayName

@Composable
fun UserListContent(component: UserListComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.actionError ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = component::onRefresh) {
                Icon(painterResource(Res.drawable.ic_refresh), contentDescription = "Обновить")
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LoadStateContent(state.users, onRetry = component::onRefresh) { users ->
                // Bottom padding keeps the last row clear of the «Добавить» button.
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(users, key = { it.id.value }) { user ->
                        UserRow(
                            user = user,
                            isCurrent = user.id == state.currentUserId,
                            isBusy = user.id == state.busyUserId,
                            onBlock = { component.onBlockClick(user) },
                            onUnblock = { component.onUnblockClick(user) },
                        )
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = component::onCreateClick,
                icon = { Icon(painterResource(Res.drawable.ic_add), contentDescription = null) },
                text = { Text("Добавить") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
    state.confirmingBlock?.let { user ->
        ConfirmDialog(
            title = "Заблокировать «${user.login}»?",
            text = "${user.employee.fullName} не сможет войти в систему, пока учётную запись не разблокируют.",
            confirmLabel = "Заблокировать",
            onConfirm = component::onBlockConfirm,
            onDismiss = component::onBlockDismiss,
        )
    }
}

@Composable
private fun UserRow(user: UserDto, isCurrent: Boolean, isBusy: Boolean, onBlock: () -> Unit, onUnblock: () -> Unit) {
    ListItem(
        overlineContent = { Text(if (user.isBlocked) "${user.role.displayName} · заблокирован" else user.role.displayName) },
        headlineContent = { Text(if (isCurrent) "${user.login} (вы)" else user.login) },
        supportingContent = { Text("${user.employee.fullName} · ${user.employee.department.name}") },
        trailingContent = {
            when {
                isBusy -> CircularProgressIndicator(Modifier.size(24.dp))
                user.isBlocked -> IconButton(onClick = onUnblock) {
                    Icon(painterResource(Res.drawable.ic_lock_open), contentDescription = "Разблокировать")
                }
                !isCurrent -> IconButton(onClick = onBlock) {
                    Icon(painterResource(Res.drawable.ic_lock), contentDescription = "Заблокировать")
                }
            }
        },
    )
}
