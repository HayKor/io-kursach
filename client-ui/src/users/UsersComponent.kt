package ru.haykor.sudv.client.ui.users

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.childStackWebNavigation
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.webhistory.WebNavigation
import com.arkivanov.decompose.router.webhistory.WebNavigationOwner
import com.arkivanov.decompose.value.Value
import kotlinx.serialization.Serializable
import ru.haykor.sudv.client.domain.IApiClient
import ru.haykor.sudv.client.ui.DeepLink
import ru.haykor.sudv.client.ui.main.Section
import ru.haykor.sudv.client.ui.main.SectionComponent
import ru.haykor.sudv.shared.domain.UserId

/** The «Учётные записи» section (ADMIN only): list → new account, at `/users` and `/users/new`. */
interface UsersComponent : SectionComponent, WebNavigationOwner {
    val stack: Value<ChildStack<*, Child>>

    sealed interface Child {
        class List(val component: UserListComponent) : Child
        class Form(val component: UserFormComponent) : Child
    }
}

class DefaultUsersComponent(
    componentContext: ComponentContext,
    private val apiClient: IApiClient,
    private val currentUserId: UserId,
    deepLink: DeepLink = DeepLink.None,
) : UsersComponent, ComponentContext by componentContext {
    override val section = Section.USERS

    private val navigation = StackNavigation<Config>()

    private val _stack = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialStack = { if (deepLink.first == "new") listOf(Config.List, Config.Create) else listOf(Config.List) },
        handleBackButton = true,
        childFactory = ::child,
    )
    override val stack: Value<ChildStack<*, UsersComponent.Child>> = _stack

    override val webNavigation: WebNavigation<*> = childStackWebNavigation(
        navigator = navigation,
        stack = _stack,
        serializer = Config.serializer(),
        pathMapper = { if (it.configuration == Config.Create) "new" else null },
    )

    private fun child(config: Config, context: ComponentContext): UsersComponent.Child = when (config) {
        Config.List -> UsersComponent.Child.List(
            DefaultUserListComponent(
                componentContext = context,
                apiClient = apiClient,
                currentUserId = currentUserId,
                onCreate = { navigation.navigate { listOf(Config.List, Config.Create) } },
            ),
        )
        Config.Create -> UsersComponent.Child.Form(
            DefaultUserFormComponent(
                componentContext = context,
                apiClient = apiClient,
                onSaved = {
                    _stack.value.items.forEach { (it.instance as? UsersComponent.Child.List)?.component?.reload() }
                    navigation.pop()
                },
                onCancelled = { navigation.pop() },
            ),
        )
    }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object List : Config

        @Serializable
        data object Create : Config
    }
}
