package ru.haykor.sudv.client.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import ru.haykor.sudv.client.ui.common.fullName
import ru.haykor.sudv.client.ui.resources.Res
import ru.haykor.sudv.client.ui.resources.ic_account_circle
import ru.haykor.sudv.client.ui.resources.ic_gavel
import ru.haykor.sudv.client.ui.resources.ic_group
import ru.haykor.sudv.client.ui.resources.ic_logout
import ru.haykor.sudv.client.ui.resources.ic_manage_accounts
import ru.haykor.sudv.client.ui.resources.ic_summarize
import ru.haykor.sudv.shared.contract.UserDto
import ru.haykor.sudv.shared.domain.displayName

/** Bottom bar on a narrow window, navigation rail on a wide one: [NavigationSuiteScaffold] picks by window size. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(component: MainComponent, modifier: Modifier = Modifier) {
    val stack by component.stack.subscribeAsState()
    val active = stack.active.instance.section

    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            component.sections.forEach { section ->
                item(
                    selected = section == active,
                    onClick = { component.onSectionClick(section) },
                    icon = { Icon(painterResource(section.icon), contentDescription = null) },
                    label = { Text(section.shortTitle) },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(active.title) },
                    actions = { UserMenu(component.user, component::onLogout) },
                )
            },
        ) { padding ->
            Children(stack = stack, modifier = Modifier.padding(padding)) { child ->
                SectionContent(child.instance)
            }
        }
    }
}

@Composable
private fun UserMenu(user: UserDto, onLogout: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(Res.drawable.ic_account_circle), contentDescription = "Пользователь")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(user.employee.fullName, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${user.role.displayName} · ${user.employee.department.name}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Выйти") },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_logout), contentDescription = null) },
                onClick = {
                    expanded = false
                    onLogout()
                },
            )
        }
    }
}

@Composable
private fun SectionContent(component: SectionComponent) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Раздел «${component.section.title}» в разработке", style = MaterialTheme.typography.bodyLarge)
    }
}

private val Section.icon: DrawableResource
    get() = when (this) {
        Section.PENALTIES -> Res.drawable.ic_gavel
        Section.EMPLOYEES -> Res.drawable.ic_group
        Section.REPORTS -> Res.drawable.ic_summarize
        Section.USERS -> Res.drawable.ic_manage_accounts
    }

/** Fits under an icon in the bottom bar. */
private val Section.shortTitle: String
    get() = when (this) {
        Section.PENALTIES -> "Реестр"
        Section.EMPLOYEES -> "Сотрудники"
        Section.REPORTS -> "Отчёты"
        Section.USERS -> "Учётки"
    }
