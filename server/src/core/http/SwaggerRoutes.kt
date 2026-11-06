package ru.haykor.sudv.server.core.http

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.ktor.http.ContentType
import io.ktor.openapi.*
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*

@Inject
@ContributesIntoSet(AppScope::class)
class SwaggerRoutes : RouteRegistrar {
    override fun Route.register() {
        swaggerUI("/docs") {
            info = OpenApiInfo(title = "SUDV API", version = "0.0.1")
            source = OpenApiDocSource.Routing(ContentType.Application.Json) {
                routingRoot.descendants()
            }
        }
    }
}
