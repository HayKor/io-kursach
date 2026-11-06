package ru.haykor.sudv.web.app

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import dev.zacsweers.metro.createGraph
import kotlinx.browser.document
import ru.haykor.sudv.client.ui.RootContent

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val graph = createGraph<WebAppGraph>()
    val lifecycle = LifecycleRegistry()
    val root = graph.rootFactory.create(DefaultComponentContext(lifecycle))
    // A browser tab has no lifecycle events of its own, so the components are simply resumed for the page's lifetime.
    lifecycle.resume()
    ComposeViewport(document.body!!) {
        MaterialExpressiveTheme {
            RootContent(root)
        }
    }
}
