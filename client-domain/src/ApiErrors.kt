package ru.haykor.sudv.client.domain

import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.contract.ErrorResponse

/** The server rejected the request; [message] comes from [ErrorResponse] and can be shown to the user as is. */
class ApiException(val code: ErrorCode, message: String) : Exception(message)

/** The server could not be reached or the connection broke mid-request. */
class NetworkException(cause: Throwable) : Exception("Нет связи с сервером", cause)
