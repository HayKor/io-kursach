package ru.haykor.sudv

import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.server.application.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*

fun Application.configureSwagger() {
    routing {
        swaggerUI("/docs") {
            info = OpenApiInfo(title = "SUDV API", version = "0.0.1")
            source = OpenApiDocSource.Routing(ContentType.Application.Json) {
                routingRoot.descendants()
            }
        }
    }
}
