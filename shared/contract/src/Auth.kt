package ru.haykor.sudv.shared.contract

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val login: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: UserDto,
)
