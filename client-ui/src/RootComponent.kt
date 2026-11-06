package ru.haykor.sudv.client.ui

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlinx.serialization.Serializable

interface RootComponent {
    val stack: Value<ChildStack<*, Child>>

    sealed interface Child {
        data object Main : Child
    }

    fun interface Factory {
        fun create(componentContext: ComponentContext): RootComponent
    }
}

class DefaultRootComponent(
    componentContext: ComponentContext,
) : RootComponent, ComponentContext by componentContext {
    private val navigation = StackNavigation<Config>()

    override val stack: Value<ChildStack<*, RootComponent.Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Main,
        handleBackButton = true,
        childFactory = ::child,
    )

    private fun child(config: Config, componentContext: ComponentContext): RootComponent.Child =
        when (config) {
            Config.Main -> RootComponent.Child.Main
        }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Main : Config
    }
}

@ContributesBinding(AppScope::class)
internal class DefaultRootComponentFactory : RootComponent.Factory {
    override fun create(componentContext: ComponentContext): RootComponent =
        DefaultRootComponent(componentContext = componentContext)
}