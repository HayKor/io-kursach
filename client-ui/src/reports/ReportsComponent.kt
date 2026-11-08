package ru.haykor.sudv.client.ui.reports

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.childStackWebNavigation
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushToFront
import com.arkivanov.decompose.router.webhistory.WebNavigation
import com.arkivanov.decompose.router.webhistory.WebNavigationOwner
import com.arkivanov.decompose.value.Value
import kotlinx.serialization.Serializable
import ru.haykor.sudv.client.domain.IApiClient
import ru.haykor.sudv.client.ui.DeepLink
import ru.haykor.sudv.client.ui.main.Section
import ru.haykor.sudv.client.ui.main.SectionComponent
import kotlin.time.Clock

/** The «Отчёты» section (HR): the summary report at `/reports`, the archive at `/reports/archive`. */
interface ReportsComponent : SectionComponent, WebNavigationOwner {
    val stack: Value<ChildStack<*, Child>>

    sealed interface Child {
        class Summary(val component: SummaryComponent) : Child
        class Archive(val component: ArchiveComponent) : Child
    }
}

class DefaultReportsComponent(
    componentContext: ComponentContext,
    private val apiClient: IApiClient,
    deepLink: DeepLink = DeepLink.None,
    private val clock: Clock = Clock.System,
) : ReportsComponent, ComponentContext by componentContext {
    override val section = Section.REPORTS

    private val navigation = StackNavigation<Config>()

    private val _stack = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialStack = { if (deepLink.first == ARCHIVE_PATH) listOf(Config.Summary, Config.Archive) else listOf(Config.Summary) },
        handleBackButton = true,
        childFactory = ::child,
    )
    override val stack: Value<ChildStack<*, ReportsComponent.Child>> = _stack

    override val webNavigation: WebNavigation<*> = childStackWebNavigation(
        navigator = navigation,
        stack = _stack,
        serializer = Config.serializer(),
        pathMapper = { if (it.configuration == Config.Archive) ARCHIVE_PATH else "" },
    )

    private fun child(config: Config, context: ComponentContext): ReportsComponent.Child = when (config) {
        Config.Summary -> ReportsComponent.Child.Summary(
            DefaultSummaryComponent(context, apiClient, onArchive = { navigation.pushToFront(Config.Archive) }, clock = clock),
        )
        Config.Archive -> ReportsComponent.Child.Archive(
            DefaultArchiveComponent(context, apiClient, onClose = { navigation.pop() }),
        )
    }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Summary : Config

        @Serializable
        data object Archive : Config
    }

    private companion object {
        const val ARCHIVE_PATH = "archive"
    }
}
