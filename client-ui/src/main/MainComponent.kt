package ru.haykor.sudv.client.ui.main

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ru.haykor.sudv.client.domain.IApiClient
import ru.haykor.sudv.shared.contract.UserDto

/** The signed-in shell; screens for each role will hang off it. */
interface MainComponent {
    val user: UserDto

    fun onLogout()
}

class DefaultMainComponent(
    componentContext: ComponentContext,
    private val apiClient: IApiClient,
    override val user: UserDto,
) : MainComponent, ComponentContext by componentContext {
    private val scope = coroutineScope()

    // The root reacts to the cleared token and opens the login screen.
    override fun onLogout() {
        scope.launch { apiClient.logout() }
    }
}
