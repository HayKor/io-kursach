package ru.haykor.sudv.android.app

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import ru.haykor.sudv.client.ui.RootComponent

@DependencyGraph(AppScope::class)
interface AndroidAppGraph {
    val rootFactory: RootComponent.Factory

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides context: Context): AndroidAppGraph
    }
}