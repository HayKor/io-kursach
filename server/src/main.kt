package ru.haykor.sudv

import dev.zacsweers.metro.createGraphFactory
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import ru.haykor.sudv.api.configureAuthentication
import ru.haykor.sudv.api.configureStatusPages
import ru.haykor.sudv.db.createSchema
import kotlin.time.Clock

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

suspend fun Application.module() {
    val graph = createGraphFactory<AppGraph.Factory>().create(environment.config, Clock.System)
    createSchema(graph.database)
    graph.bootstrap.run()

    // disposeLater() + subscribe() instead of dispose(), which blocks the calling thread until
    // the pool is fully torn down; the monitor callback must not block.
    monitor.subscribe(ApplicationStopped) {
        graph.connectionPool.disposeLater().subscribe()
    }

    install(ContentNegotiation) {
        json()
    }
    install(CallLogging)
    configureStatusPages()
    configureAuthentication(graph.authService, graph.jwtService)

    configureRouting(graph.authService, graph.employeeRepository)
    configureSwagger()
}
