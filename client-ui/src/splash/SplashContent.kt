package ru.haykor.sudv.client.ui.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState

@Composable
fun SplashContent(component: SplashComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        when (val current = state) {
            SplashComponent.State.Loading -> CircularProgressIndicator()
            is SplashComponent.State.Failed -> {
                Text(current.message, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = component::onRetry) { Text("Повторить") }
            }
        }
    }
}
