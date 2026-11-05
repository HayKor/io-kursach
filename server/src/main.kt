package ru.haykor.sudv

import dev.zacsweers.metro.createGraphFactory
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val graph = createGraphFactory<AppGraph.Factory>().create(environment.config)
    install(ContentNegotiation) {
        json()
    }
    configureRouting(graph.modelRepository)
}
