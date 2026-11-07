package ru.haykor.sudv.client.ui.penalties

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
import ru.haykor.sudv.client.ui.main.canManagePenalties
import ru.haykor.sudv.shared.domain.PenaltyId
import ru.haykor.sudv.shared.domain.UserRole
import kotlin.time.Clock

/** The «Реестр взысканий» section: registry → card, registration form; URLs `/penalties`, `/penalties/42`, `/penalties/new`. */
interface PenaltiesComponent : SectionComponent, WebNavigationOwner {
    val stack: Value<ChildStack<*, Child>>

    sealed interface Child {
        class List(val component: PenaltyListComponent) : Child
        class Details(val component: PenaltyDetailsComponent) : Child
        class Form(val component: PenaltyFormComponent) : Child
    }
}

/** A plain stack for the same reason as in the employees section (see `ListDetailLayout`). */
class DefaultPenaltiesComponent(
    componentContext: ComponentContext,
    private val apiClient: IApiClient,
    private val role: UserRole,
    deepLink: DeepLink = DeepLink.None,
    private val clock: Clock = Clock.System,
) : PenaltiesComponent, ComponentContext by componentContext {
    override val section = Section.PENALTIES

    private val navigation = StackNavigation<Config>()

    private val _stack = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialStack = { initialStack(deepLink) },
        handleBackButton = true,
        childFactory = ::child,
    )
    override val stack: Value<ChildStack<*, PenaltiesComponent.Child>> = _stack

    override val webNavigation: WebNavigation<*> = childStackWebNavigation(
        navigator = navigation,
        stack = _stack,
        serializer = Config.serializer(),
        pathMapper = { it.configuration.path },
    )

    private fun child(config: Config, context: ComponentContext): PenaltiesComponent.Child = when (config) {
        Config.List -> PenaltiesComponent.Child.List(
            DefaultPenaltyListComponent(
                componentContext = context,
                apiClient = apiClient,
                role = role,
                // Opening from the list replaces whatever card or form was open next to it on a wide screen.
                onOpen = { id -> navigation.navigate { listOf(Config.List, Config.Details(id)) } },
                onCreate = { navigation.navigate { listOf(Config.List, Config.Create) } },
            ),
        )
        is Config.Details -> PenaltiesComponent.Child.Details(
            DefaultPenaltyDetailsComponent(
                componentContext = context,
                apiClient = apiClient,
                penaltyId = config.id,
                role = role,
                onChanged = ::reloadList,
                onClose = { navigation.pop() },
                clock = clock,
            ),
        )
        Config.Create -> PenaltiesComponent.Child.Form(
            DefaultPenaltyFormComponent(
                componentContext = context,
                apiClient = apiClient,
                onSaved = { created ->
                    reloadList()
                    navigation.navigate { stack -> stack.dropLast(1) + Config.Details(created.id) }
                },
                onCancelled = { navigation.pop() },
                clock = clock,
            ),
        )
    }

    private fun initialStack(deepLink: DeepLink): List<Config> {
        val first = deepLink.first
        if (first == "new") return if (role.canManagePenalties) listOf(Config.List, Config.Create) else listOf(Config.List)
        val id = first?.toLongOrNull()?.let(::PenaltyId) ?: return listOf(Config.List)
        return listOf(Config.List, Config.Details(id))
    }

    private fun reloadList() =
        _stack.value.items.mapNotNull { it.instance as? PenaltiesComponent.Child.List }.forEach { it.component.reload() }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object List : Config

        @Serializable
        data class Details(val id: PenaltyId) : Config

        @Serializable
        data object Create : Config
    }

    private val Config.path: String
        get() = when (this) {
            Config.List -> ""
            is Config.Details -> "${id.value}"
            Config.Create -> "new"
        }
}
