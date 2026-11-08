package ru.haykor.sudv.client.ui.main

/** One section's screens; `Main` creates it in `section(...)` and `SectionContent` picks its screen by type. */
interface SectionComponent {
    val section: Section
}
