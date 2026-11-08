package ru.haykor.sudv.client.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal const val DATA_STORE_FILE_NAME = "app.preferences_pb"

/** Keeps the access token between app restarts. */
interface ITokenStorage {
    val token: Flow<String?>
    suspend fun save(token: String)
    suspend fun clear()
}

private val TOKEN_KEY = stringPreferencesKey("token")

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
internal class DataStoreTokenStorage(private val dataStore: DataStore<Preferences>) : ITokenStorage {
    override val token: Flow<String?> = dataStore.data.map { it[TOKEN_KEY] }

    override suspend fun save(token: String) {
        dataStore.edit { it[TOKEN_KEY] = token }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(TOKEN_KEY) }
    }
}
