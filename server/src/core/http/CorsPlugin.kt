package ru.haykor.sudv.server.core.http

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import ru.haykor.sudv.server.core.config.AppConfig

/**
 * Lets the web client, served from another origin, call the API. The token travels in a header, not a cookie,
 * so an allowed origin cannot make requests on a user's behalf without the user's token.
 */
@Inject
@ContributesIntoSet(AppScope::class)
class CorsPlugin(private val appConfig: AppConfig) : PluginInstaller {
    override val order = PluginOrder.CORS

    override fun Application.install() {
        val origins = appConfig.corsAllowedOrigins.toSet()
        install(CORS) {
            allowOrigins { it in origins }
            allowHeader(HttpHeaders.Authorization)
            // Also allows JSON bodies: Ktor otherwise rejects non-simple content types.
            allowHeader(HttpHeaders.ContentType)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            // Carries the file name of downloads; browsers hide non-safelisted response headers from scripts.
            exposeHeader(HttpHeaders.ContentDisposition)
        }
    }
}
