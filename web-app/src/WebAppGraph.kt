package ru.haykor.sudv.web.app

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import ru.haykor.sudv.client.data.ApiConfig
import ru.haykor.sudv.client.ui.RootComponent

@DependencyGraph(AppScope::class)
interface WebAppGraph {
    val rootFactory: RootComponent.Factory

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides apiConfig: ApiConfig): WebAppGraph
    }
}
