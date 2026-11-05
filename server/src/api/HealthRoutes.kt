package ru.haykor.sudv.api

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

@Inject
@ContributesIntoSet(AppScope::class)
class HealthRoutes : RouteRegistrar {
    override fun Route.register() {
        /** Tag: Health */
        get("/health") {
            call.respondText("OK")
        }
    }
}
