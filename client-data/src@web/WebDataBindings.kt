package ru.haykor.sudv.client.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.WebLocalStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

@BindingContainer
@ContributesTo(AppScope::class)
object WebDataBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun dataStore(): DataStore<Preferences> = DataStoreFactory.create(
        storage = WebLocalStorage(
            serializer = PreferencesSerializer,
            name = DATA_STORE_FILE_NAME,
        ),
    )

    // CIO is not an option here: its sockets need Node.js and fail in a browser.
    @Provides
    @SingleIn(AppScope::class)
    fun httpClientEngine(): HttpClientEngine = Js.create()

    @Provides
    fun apiConfig(): ApiConfig = ApiConfig(baseUrl = "http://localhost:8080")
}
