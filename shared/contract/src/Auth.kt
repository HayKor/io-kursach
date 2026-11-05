package ru.haykor.sudv

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val login: String,
    val password: String,
)

/** [token] передаётся в остальные запросы в заголовке `Authorization: Bearer <token>`. */
@Serializable
data class LoginResponse(
    val token: String,
    val user: UserDto,
)
