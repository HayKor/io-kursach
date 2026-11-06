package ru.haykor.sudv.web.app

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import ru.haykor.sudv.client.ui.RootComponent

@DependencyGraph(AppScope::class)
interface WebAppGraph {
    val rootFactory: RootComponent.Factory
}
