package ru.haykor.sudv.android.app

import android.app.Application
import dev.zacsweers.metro.createGraphFactory

class SudvApplication : Application() {
    val graph by lazy {
        createGraphFactory<AndroidAppGraph.Factory>().create(this)
    }
}
