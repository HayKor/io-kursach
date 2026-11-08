package ru.haykor.sudv.client.domain

import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.contract.ErrorResponse

/** Every failure [IApiClient] reports; [message] can be shown to the user as is. */
sealed class ApiClientException(override val message: String, cause: Throwable? = null) : Exception(message, cause)

/** The server rejected the request; [message] comes from [ErrorResponse]. */
class ApiException(val code: ErrorCode, message: String) : ApiClientException(message)

/** The server could not be reached or the connection broke mid-request. */
class NetworkException(cause: Throwable) : ApiClientException("Нет связи с сервером", cause)
