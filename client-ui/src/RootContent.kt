package ru.haykor.sudv.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import ru.haykor.sudv.client.ui.login.LoginContent
import ru.haykor.sudv.client.ui.main.MainContent
import ru.haykor.sudv.client.ui.splash.SplashContent

@Composable
fun RootContent(component: RootComponent, modifier: Modifier = Modifier) {
    Children(stack = component.stack, modifier = modifier) { child ->
        when (val instance = child.instance) {
            is RootComponent.Child.Splash -> SplashContent(instance.component)
            is RootComponent.Child.Login -> LoginContent(instance.component)
            is RootComponent.Child.Main -> MainContent(instance.component)
        }
    }
}
