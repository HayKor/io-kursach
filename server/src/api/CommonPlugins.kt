package ru.haykor.sudv.api

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

@Inject
@ContributesIntoSet(AppScope::class)
class CallLoggingPlugin : PluginInstaller {
    override val order = PluginOrder.CALL_LOGGING

    override fun Application.install() {
        install(CallLogging)
    }
}

@Inject
@ContributesIntoSet(AppScope::class)
class ContentNegotiationPlugin : PluginInstaller {
    override val order = PluginOrder.CONTENT_NEGOTIATION

    override fun Application.install() {
        install(ContentNegotiation) {
            json()
        }
    }
}
