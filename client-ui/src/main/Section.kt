package ru.haykor.sudv.client.ui.main

import kotlinx.serialization.Serializable
import ru.haykor.sudv.shared.domain.UserRole

/** A top-level part of the app; [path] is its first URL segment in the web client. */
@Serializable
enum class Section(val title: String, val path: String) {
    PENALTIES("Реестр взысканий", "penalties"),
    EMPLOYEES("Сотрудники", "employees"),
    REPORTS("Отчёты", "reports"),
    USERS("Учётные записи", "users"),
}

/** Sections a role may open, first one is the start screen; mirrors the server's permission matrix. */
fun sectionsFor(role: UserRole): List<Section> = when (role) {
    UserRole.ADMIN -> listOf(Section.USERS, Section.EMPLOYEES)
    UserRole.HR -> listOf(Section.PENALTIES, Section.EMPLOYEES, Section.REPORTS)
    UserRole.MANAGER -> listOf(Section.PENALTIES, Section.EMPLOYEES)
}
