package ru.haykor.sudv

import dev.zacsweers.metro.createGraphFactory
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.AttributeKey
import ru.haykor.sudv.db.createSchema
import kotlin.time.Clock

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

/** Lets tests reach the graph through [Application.attributes], e.g. to call [AppGraphTestAccessors.expiryService] directly. */
val AppGraphKey = AttributeKey<AppGraph>("AppGraph")

suspend fun Application.module() {
    val graph = createGraphFactory<AppGraph.Factory>().create(environment.config, Clock.System)
    attributes.put(AppGraphKey, graph)
    createSchema(graph.database)
    graph.bootstrap.run()

    // disposeLater() + subscribe() instead of dispose(), which blocks the calling thread until
    // the pool is fully torn down; the monitor callback must not block.
    monitor.subscribe(ApplicationStopped) {
        graph.connectionPool.disposeLater().subscribe()
    }

    graph.plugins.sortedBy { it.order }.forEach { with(it) { install() } }
    routing {
        graph.routes.forEach { with(it) { register() } }
    }
    graph.expiryScheduler.start(this)
}
