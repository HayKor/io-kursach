package ru.haykor.sudv.client.ui.login

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ru.haykor.sudv.client.domain.ApiClientException
import ru.haykor.sudv.client.domain.IApiClient
import ru.haykor.sudv.shared.contract.LoginRequest
import ru.haykor.sudv.shared.contract.UserDto

interface LoginComponent {
    val state: Value<State>

    fun onLoginChange(value: String)
    fun onPasswordChange(value: String)
    fun onSubmit()

    data class State(
        val login: String = "",
        val password: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
    ) {
        val canSubmit: Boolean get() = login.isNotBlank() && password.isNotEmpty() && !isLoading
    }
}

class DefaultLoginComponent(
    componentContext: ComponentContext,
    private val apiClient: IApiClient,
    private val onSignedIn: (UserDto) -> Unit,
) : LoginComponent, ComponentContext by componentContext {
    private val scope = coroutineScope()
    private val _state = MutableValue(LoginComponent.State())
    override val state: Value<LoginComponent.State> = _state

    override fun onLoginChange(value: String) = _state.update { it.copy(login = value, error = null) }

    override fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    override fun onSubmit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            try {
                val response = apiClient.login(LoginRequest(current.login, current.password))
                onSignedIn(response.user)
            } catch (e: ApiClientException) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
