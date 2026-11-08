package ru.haykor.sudv.android.app

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import ru.haykor.sudv.client.data.ApiConfig
import ru.haykor.sudv.client.ui.RootComponent

@DependencyGraph(AppScope::class)
interface AndroidAppGraph {
    val rootFactory: RootComponent.Factory

    @Provides
    fun apiConfig(context: Context): ApiConfig = ApiConfig(context.getString(R.string.api_base_url))

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides context: Context): AndroidAppGraph
    }
}
