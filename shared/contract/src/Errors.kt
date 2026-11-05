package ru.haykor.sudv

import kotlinx.serialization.Serializable

/** Тело ответа при ошибке. [message] — текст для показа пользователю. */
@Serializable
data class ErrorResponse(
    val code: ErrorCode,
    val message: String,
)

/** Коды ошибок API и соответствующие им HTTP-статусы. */
enum class ErrorCode(val httpStatus: Int) {
    /** Не заполнено обязательное поле или значение недопустимо. */
    VALIDATION_FAILED(400),

    /** Неверный логин или пароль. */
    INVALID_CREDENTIALS(401),

    /** Токен отсутствует, неверен или истёк. */
    UNAUTHORIZED(401),

    /** Учётная запись заблокирована. */
    USER_BLOCKED(403),

    /** Роли пользователя не хватает прав на операцию. */
    FORBIDDEN(403),

    NOT_FOUND(404),

    /** Сотрудник с таким табельным номером уже есть. */
    DUPLICATE_PERSONNEL_NUMBER(409),

    /** Учётная запись с таким логином уже есть. */
    DUPLICATE_LOGIN(409),

    /** Снять досрочно можно только действующее взыскание. */
    PENALTY_NOT_ACTIVE(409),

    /** Файл превышает допустимый размер. */
    FILE_TOO_LARGE(413),

    /** Допустимы только PDF и JPEG. */
    UNSUPPORTED_FILE_TYPE(415),

    INTERNAL_ERROR(500),
}
