package ru.haykor.sudv.client.ui

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.childStackWebNavigation
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.router.webhistory.WebNavigation
import com.arkivanov.decompose.router.webhistory.WebNavigationOwner
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import ru.haykor.sudv.client.domain.IApiClient
import ru.haykor.sudv.client.ui.login.DefaultLoginComponent
import ru.haykor.sudv.client.ui.login.LoginComponent
import ru.haykor.sudv.client.ui.main.DefaultMainComponent
import ru.haykor.sudv.client.ui.main.MainComponent
import ru.haykor.sudv.client.ui.splash.DefaultSplashComponent
import ru.haykor.sudv.client.ui.splash.SplashComponent
import ru.haykor.sudv.shared.contract.UserDto

interface RootComponent : WebNavigationOwner {
    val stack: Value<ChildStack<*, Child>>

    sealed interface Child {
        class Splash(val component: SplashComponent) : Child
        class Login(val component: LoginComponent) : Child
        class Main(val component: MainComponent) : Child
    }

    fun interface Factory {
        /** [deepLink] is the URL the web client was opened with; [DeepLink.None] on Android. */
        fun create(componentContext: ComponentContext, deepLink: DeepLink): RootComponent
    }
}

/**
 * Starts on [Config.Splash], which restores the session from a saved token. Leaving the session is handled here, not
 * by the children: whenever [IApiClient.isSignedIn] turns `false` (logout or a rejected token), the stack is replaced
 * with the login screen. A [deepLink] waits until the user is signed in and then picks the screen inside [Config.Main].
 */
class DefaultRootComponent(
    componentContext: ComponentContext,
    private val apiClient: IApiClient,
    deepLink: DeepLink = DeepLink.None,
) : RootComponent, ComponentContext by componentContext {
    private val navigation = StackNavigation<Config>()
    private val scope = coroutineScope()

    // Declared before the stack: a restored stack may create Main right away. Used once, by the first Main.
    private var pendingDeepLink = deepLink

    private val _stack = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Splash,
        handleBackButton = true,
        childFactory = ::child,
    )
    override val stack: Value<ChildStack<*, RootComponent.Child>> = _stack

    override val webNavigation: WebNavigation<*> = childStackWebNavigation(
        navigator = navigation,
        stack = _stack,
        serializer = Config.serializer(),
        pathMapper = { if (it.configuration == Config.Login) "login" else null },
        childSelector = { (it.instance as? RootComponent.Child.Main)?.component },
    )

    init {
        scope.launch {
            apiClient.isSignedIn.collect { signedIn ->
                if (!signedIn && stack.value.active.configuration != Config.Login) {
                    navigation.replaceAll(Config.Login)
                }
            }
        }
    }

    private fun child(config: Config, componentContext: ComponentContext): RootComponent.Child = when (config) {
        Config.Splash -> RootComponent.Child.Splash(
            DefaultSplashComponent(componentContext, apiClient, onRestored = ::openMain),
        )
        Config.Login -> RootComponent.Child.Login(
            DefaultLoginComponent(componentContext, apiClient, onSignedIn = ::openMain),
        )
        is Config.Main -> RootComponent.Child.Main(
            DefaultMainComponent(componentContext, apiClient, config.user, pendingDeepLink)
                .also { pendingDeepLink = DeepLink.None },
        )
    }

    private fun openMain(user: UserDto) {
        navigation.replaceAll(Config.Main(user))
    }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Splash : Config

        @Serializable
        data object Login : Config

        @Serializable
        data class Main(val user: UserDto) : Config
    }
}

@Inject
@ContributesBinding(AppScope::class)
internal class DefaultRootComponentFactory(private val apiClient: IApiClient) : RootComponent.Factory {
    override fun create(componentContext: ComponentContext, deepLink: DeepLink): RootComponent =
        DefaultRootComponent(componentContext, apiClient, deepLink)
}
