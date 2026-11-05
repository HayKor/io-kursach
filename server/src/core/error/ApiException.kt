package ru.haykor.sudv.server.core.error

import ru.haykor.sudv.shared.contract.ErrorCode

/** Services report domain errors only through this exception; StatusPages maps it to ErrorResponse. */
class ApiException(val code: ErrorCode, message: String) : RuntimeException(message)
