package ru.haykor.sudv.client.ui.main

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.childStackWebNavigation
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.webhistory.WebNavigation
import com.arkivanov.decompose.router.webhistory.WebNavigationOwner
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ru.haykor.sudv.client.domain.IApiClient
import ru.haykor.sudv.client.ui.DeepLink
import ru.haykor.sudv.shared.contract.UserDto

/** The signed-in shell: the sections of the user's role, one of them open. */
interface MainComponent : WebNavigationOwner {
    val user: UserDto
    val sections: List<Section>
    val stack: Value<ChildStack<*, SectionComponent>>

    fun onSectionClick(section: Section)
    fun onLogout()
}

/**
 * Sections live in one stack, the open one on top; a section left in the back stack keeps its component and
 * therefore its own history. [deepLink] picks the starting section if the role may open it.
 */
class DefaultMainComponent(
    componentContext: ComponentContext,
    private val apiClient: IApiClient,
    override val user: UserDto,
    deepLink: DeepLink = DeepLink.None,
) : MainComponent, ComponentContext by componentContext {
    private val scope = coroutineScope()
    private val navigation = StackNavigation<Section>()

    override val sections: List<Section> = sectionsFor(user.role)

    private val _stack = childStack(
        source = navigation,
        serializer = Section.serializer(),
        initialConfiguration = sections.firstOrNull { it.path == deepLink.first } ?: sections.first(),
        // Back inside a section is handled by the section; from a section's start screen it leaves the app.
        handleBackButton = false,
        childFactory = { section, context -> PlaceholderSectionComponent(context, section) },
    )
    override val stack: Value<ChildStack<*, SectionComponent>> = _stack

    // Tab-like: the browser history follows only the open section, not the order sections were visited in.
    override val webNavigation: WebNavigation<*> = childStackWebNavigation(
        navigator = navigation,
        stack = _stack,
        serializer = Section.serializer(),
        enableHistory = false,
        pathMapper = { it.configuration.path },
    )

    override fun onSectionClick(section: Section) {
        // Not bringToFront: it drops every configuration of the same class, i.e. all other sections of this enum.
        if (section in sections) navigation.navigate { stack -> stack.filterNot { it == section } + section }
    }

    // The root reacts to the cleared token and opens the login screen.
    override fun onLogout() {
        scope.launch { apiClient.logout() }
    }
}
