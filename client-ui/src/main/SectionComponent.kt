package ru.haykor.sudv.client.ui.main

import com.arkivanov.decompose.ComponentContext

/** One section's screens. Placeholder until the section is built in its own phase. */
interface SectionComponent {
    val section: Section
}

class PlaceholderSectionComponent(
    componentContext: ComponentContext,
    override val section: Section,
) : SectionComponent, ComponentContext by componentContext
