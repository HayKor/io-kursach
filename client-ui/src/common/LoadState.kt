package ru.haykor.sudv.client.ui.common

import ru.haykor.sudv.client.domain.ApiClientException
import ru.haykor.sudv.client.domain.ApiException
import ru.haykor.sudv.shared.contract.ErrorCode

/** What a screen shows while its data comes from the server. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    /** [canRetry] is `false` when asking again cannot help, e.g. no permission or no such record. */
    data class Failed(val message: String, val canRetry: Boolean = true) : LoadState<Nothing>
    data class Loaded<T>(val data: T) : LoadState<T>
}

/** Runs [block] and turns a client failure into [LoadState.Failed] with a message for the user. */
suspend fun <T> load(block: suspend () -> T): LoadState<T> =
    try {
        LoadState.Loaded(block())
    } catch (e: ApiClientException) {
        LoadState.Failed(e.message, canRetry = !(e is ApiException && e.code in FINAL_ERRORS))
    }

private val FINAL_ERRORS = setOf(ErrorCode.FORBIDDEN, ErrorCode.NOT_FOUND)
