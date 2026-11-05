package ru.haykor.sudv

import android.app.Application
import dev.zacsweers.metro.createGraphFactory

class SudvApplication : Application() {
    val graph: AppGraph by lazy {
        createGraphFactory<AppGraph.Factory>().create(this)
    }
}
