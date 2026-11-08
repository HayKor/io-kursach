package ru.haykor.sudv.web.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.router.webhistory.withWebHistory
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import dev.zacsweers.metro.createGraphFactory
import kotlinx.browser.document
import kotlinx.browser.window
import ru.haykor.sudv.client.data.ApiConfig
import ru.haykor.sudv.client.ui.DeepLink
import ru.haykor.sudv.client.ui.RootContent
import ru.haykor.sudv.client.ui.theme.SudvTheme

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val configured = document.querySelector("meta[name=sudv-api]")?.getAttribute("content")
    val apiConfig = ApiConfig.of(configured, fallback = window.location.origin)
    val graph = createGraphFactory<WebAppGraph.Factory>().create(apiConfig)
    val lifecycle = LifecycleRegistry()
    // Ties navigation to the browser history: back/forward buttons, a URL per screen, state kept across reloads.
    val root = withWebHistory { stateKeeper, deepLink ->
        graph.rootFactory.create(DefaultComponentContext(lifecycle, stateKeeper), DeepLink.parse(deepLink))
    }
    // A browser tab has no lifecycle events of its own, so the components are simply resumed for the page's lifetime.
    lifecycle.resume()
    ComposeViewport(document.body!!) {
        SudvTheme {
            RootContent(root)
        }
    }
}
