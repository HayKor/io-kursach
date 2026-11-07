package ru.haykor.sudv.client.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.FileStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

@BindingContainer
@ContributesTo(AppScope::class)
object AndroidDataBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun dataStore(context: Context): DataStore<Preferences> = DataStoreFactory.create(
        storage = FileStorage(
            serializer = PreferencesFileSerializer,
            produceFile = { context.filesDir.resolve(DATA_STORE_FILE_NAME) },
        ),
    )

    @Provides
    @SingleIn(AppScope::class)
    fun httpClientEngine(): HttpClientEngine = OkHttp.create()
}
