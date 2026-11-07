package ru.haykor.sudv.client.ui.employees

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.childStackWebNavigation
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.router.webhistory.WebNavigation
import com.arkivanov.decompose.router.webhistory.WebNavigationOwner
import com.arkivanov.decompose.value.Value
import kotlinx.serialization.Serializable
import ru.haykor.sudv.client.domain.IApiClient
import ru.haykor.sudv.client.ui.DeepLink
import ru.haykor.sudv.client.ui.main.Section
import ru.haykor.sudv.client.ui.main.SectionComponent
import ru.haykor.sudv.client.ui.main.canCreateEmployees
import ru.haykor.sudv.client.ui.main.canEditEmployees
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.UserRole

/** The «Сотрудники» section: list → card → form, each with its own URL (`/employees`, `/employees/7`, `/employees/7/edit`). */
interface EmployeesComponent : SectionComponent, WebNavigationOwner {
    val stack: Value<ChildStack<*, Child>>

    sealed interface Child {
        class List(val component: EmployeeListComponent) : Child
        class Details(val component: EmployeeDetailsComponent) : Child
        class Form(val component: EmployeeFormComponent) : Child
    }
}

/**
 * A plain stack rather than Child Panels: panels replace the browser history entry, so the browser's back button
 * would leave the section instead of closing the card. The wide layout puts the list next to the top screen itself.
 */
class DefaultEmployeesComponent(
    componentContext: ComponentContext,
    private val apiClient: IApiClient,
    private val role: UserRole,
    deepLink: DeepLink = DeepLink.None,
) : EmployeesComponent, ComponentContext by componentContext {
    override val section = Section.EMPLOYEES

    private val navigation = StackNavigation<Config>()

    private val _stack = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialStack = { initialStack(deepLink) },
        handleBackButton = true,
        childFactory = ::child,
    )
    override val stack: Value<ChildStack<*, EmployeesComponent.Child>> = _stack

    override val webNavigation: WebNavigation<*> = childStackWebNavigation(
        navigator = navigation,
        stack = _stack,
        serializer = Config.serializer(),
        pathMapper = { it.configuration.path },
    )

    private fun child(config: Config, context: ComponentContext): EmployeesComponent.Child = when (config) {
        Config.List -> EmployeesComponent.Child.List(
            DefaultEmployeeListComponent(
                componentContext = context,
                apiClient = apiClient,
                role = role,
                // Opening from the list replaces whatever card or form was open next to it on a wide screen.
                onOpen = { id -> navigation.navigate { listOf(Config.List, Config.Details(id)) } },
                onCreate = { navigation.navigate { listOf(Config.List, Config.Create) } },
            ),
        )
        is Config.Details -> EmployeesComponent.Child.Details(
            DefaultEmployeeDetailsComponent(
                componentContext = context,
                apiClient = apiClient,
                employeeId = config.id,
                role = role,
                onEdit = { navigation.pushNew(Config.Edit(config.id)) },
                onChanged = ::reloadList,
                onClose = { navigation.pop() },
            ),
        )
        Config.Create -> EmployeesComponent.Child.Form(
            DefaultEmployeeFormComponent(
                componentContext = context,
                apiClient = apiClient,
                employeeId = null,
                onSaved = { saved ->
                    reloadList()
                    navigation.navigate { stack -> stack.dropLast(1) + Config.Details(saved.id) }
                },
                onCancelled = { navigation.pop() },
            ),
        )
        is Config.Edit -> EmployeesComponent.Child.Form(
            DefaultEmployeeFormComponent(
                componentContext = context,
                apiClient = apiClient,
                employeeId = config.id,
                onSaved = {
                    reloadList()
                    navigation.pop { reloadDetails() }
                },
                onCancelled = { navigation.pop() },
            ),
        )
    }

    private fun initialStack(deepLink: DeepLink): List<Config> {
        val first = deepLink.first
        if (first == "new") return if (role.canCreateEmployees) listOf(Config.List, Config.Create) else listOf(Config.List)
        val id = first?.toLongOrNull()?.let(::EmployeeId) ?: return listOf(Config.List)
        val edit = deepLink.dropFirst().first == "edit" && role.canEditEmployees
        return listOfNotNull(Config.List, Config.Details(id), Config.Edit(id).takeIf { edit })
    }

    private fun reloadList() = children<EmployeesComponent.Child.List>().forEach { it.component.reload() }

    private fun reloadDetails() = children<EmployeesComponent.Child.Details>().forEach { it.component.reload() }

    private inline fun <reified T : EmployeesComponent.Child> children(): List<T> =
        _stack.value.items.mapNotNull { it.instance as? T }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object List : Config

        @Serializable
        data class Details(val id: EmployeeId) : Config

        @Serializable
        data object Create : Config

        @Serializable
        data class Edit(val id: EmployeeId) : Config
    }

    private val Config.path: String
        get() = when (this) {
            Config.List -> ""
            is Config.Details -> "${id.value}"
            Config.Create -> "new"
            is Config.Edit -> "${id.value}/edit"
        }
}
