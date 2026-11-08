package ru.haykor.sudv.server.core.http

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.ktor.http.ContentType
import io.ktor.http.defaultForFileExtension
import io.ktor.server.application.call
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import ru.haykor.sudv.server.core.config.AppConfig
import java.io.File

// Paths that belong to the server; an unknown one here is a real 404, not a client-side page.
private val SERVER_PREFIXES = listOf("api/", "docs")

/**
 * Serves the built web client on `/`. Every unknown path gets `index.html`: the client keeps its screen in the URL
 * (e.g. `/penalties/42`), so a reload or a shared link must load the app, which then opens that screen itself.
 */
@Inject
@ContributesIntoSet(AppScope::class)
class WebClientRoutes(private val appConfig: AppConfig) : RouteRegistrar {
    override fun Route.register() {
        val dir = appConfig.webDir?.let(::File) ?: return
        val index = File(dir, "index.html")
        staticFiles("/", dir) {
            // Browsers refuse ES modules and streaming Wasm compilation served with a generic content type.
            contentType { file ->
                when (file.extension) {
                    "mjs", "js" -> ContentType.Text.JavaScript
                    "wasm" -> ContentType("application", "wasm")
                    else -> ContentType.defaultForFileExtension(file.extension)
                }
            }
            fallback { requestedPath, call ->
                if (SERVER_PREFIXES.none { requestedPath.startsWith(it) }) call.respondFile(index)
            }
        }
    }
}
