package ru.haykor.sudv.shared.contract

import kotlinx.serialization.Serializable

/** [message] is meant to be shown to the user as is. */
@Serializable
data class ErrorResponse(
    val code: ErrorCode,
    val message: String,
)

enum class ErrorCode(val httpStatus: Int) {
    VALIDATION_FAILED(400),
    INVALID_CREDENTIALS(401),

    /** Token is missing, invalid or expired. */
    UNAUTHORIZED(401),
    USER_BLOCKED(403),
    FORBIDDEN(403),
    NOT_FOUND(404),
    DUPLICATE_PERSONNEL_NUMBER(409),
    DUPLICATE_LOGIN(409),

    /** Only an active penalty can be removed early. */
    PENALTY_NOT_ACTIVE(409),
    FILE_TOO_LARGE(413),

    /** Only PDF and JPEG are accepted. */
    UNSUPPORTED_FILE_TYPE(415),
    INTERNAL_ERROR(500),
}
