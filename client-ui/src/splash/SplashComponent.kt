package ru.haykor.sudv.client.ui.splash

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.haykor.sudv.client.domain.ApiClientException
import ru.haykor.sudv.client.domain.ApiException
import ru.haykor.sudv.client.domain.IApiClient
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.contract.UserDto

interface SplashComponent {
    val state: Value<State>

    fun onRetry()

    sealed interface State {
        data object Loading : State
        data class Failed(val message: String) : State
    }
}

/** Restores the session from a saved token. Without a token it does nothing: the root then opens the login screen. */
class DefaultSplashComponent(
    componentContext: ComponentContext,
    private val apiClient: IApiClient,
    private val onRestored: (UserDto) -> Unit,
) : SplashComponent, ComponentContext by componentContext {
    private val scope = coroutineScope()
    private val _state = MutableValue<SplashComponent.State>(SplashComponent.State.Loading)
    override val state: Value<SplashComponent.State> = _state

    init {
        restore()
    }

    override fun onRetry() = restore()

    private fun restore() {
        _state.value = SplashComponent.State.Loading
        scope.launch {
            if (!apiClient.isSignedIn.first()) return@launch
            try {
                onRestored(apiClient.me())
            } catch (e: ApiClientException) {
                // A rejected token is already cleared by the client, and the root switches to the login screen.
                if (e is ApiException && e.code == ErrorCode.UNAUTHORIZED) return@launch
                _state.value = SplashComponent.State.Failed(e.message)
            }
        }
    }
}
