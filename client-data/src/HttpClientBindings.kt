package ru.haykor.sudv.client.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Platforms differ only in the engine, provided by their own binding container. */
@BindingContainer
@ContributesTo(AppScope::class)
object HttpClientBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun httpClient(engine: HttpClientEngine, config: ApiConfig): HttpClient = createHttpClient(engine, config)
}

internal fun createHttpClient(engine: HttpClientEngine, config: ApiConfig): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        // A field added on the server must not break an older client.
        json(Json { ignoreUnknownKeys = true })
    }
    defaultRequest { url(config.baseUrl) }
}
