package ru.haykor.sudv.web.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import dev.zacsweers.metro.createGraph
import kotlinx.browser.document
import ru.haykor.sudv.client.ui.RootContent

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val graph = createGraph<WebAppGraph>()
    val root = graph.rootFactory.create(DefaultComponentContext(LifecycleRegistry()))
    ComposeViewport(document.body!!) {
        RootContent(root)
    }
}