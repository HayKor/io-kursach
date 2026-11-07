package ru.haykor.sudv.client.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.haykor.sudv.client.ui.common.fullName
import ru.haykor.sudv.shared.domain.displayName

@Composable
fun MainContent(component: MainComponent, modifier: Modifier = Modifier) {
    val user = component.user
    val employee = user.employee
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(employee.fullName, style = MaterialTheme.typography.titleLarge)
        Text("${user.role.displayName} · ${employee.department.name}", style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = component::onLogout) { Text("Выйти") }
    }
}
