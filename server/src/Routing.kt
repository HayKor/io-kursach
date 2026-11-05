package ru.haykor.sudv

import io.ktor.server.application.*
import io.ktor.server.routing.*
import ru.haykor.sudv.api.RouteRegistrar

fun Application.configureRouting(routes: Set<RouteRegistrar>) {
    routing {
        routes.forEach { with(it) { register() } }
    }
}
