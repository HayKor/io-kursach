package ru.haykor.sudv.client.ui.splash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ru.haykor.sudv.client.ui.common.ErrorContent
import ru.haykor.sudv.client.ui.common.LoadingContent

@Composable
fun SplashContent(component: SplashComponent, modifier: Modifier = Modifier) {
    val state by component.state.subscribeAsState()
    when (val current = state) {
        SplashComponent.State.Loading -> LoadingContent(modifier)
        is SplashComponent.State.Failed -> ErrorContent(current.message, component::onRetry, modifier)
    }
}
