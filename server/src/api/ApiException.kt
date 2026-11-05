package ru.haykor.sudv.api

import ru.haykor.sudv.contract.ErrorCode

/** Services report domain errors only through this exception; StatusPages maps it to ErrorResponse. */
class ApiException(val code: ErrorCode, message: String) : RuntimeException(message)
