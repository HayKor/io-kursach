package ru.haykor.sudv.client.ui.common

import ru.haykor.sudv.client.domain.ApiClientException

/** What a screen shows while its data comes from the server. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Failed(val message: String) : LoadState<Nothing>
    data class Loaded<T>(val data: T) : LoadState<T>
}

/** Runs [block] and turns a client failure into [LoadState.Failed] with a message for the user. */
suspend fun <T> load(block: suspend () -> T): LoadState<T> =
    try {
        LoadState.Loaded(block())
    } catch (e: ApiClientException) {
        LoadState.Failed(e.message)
    }
