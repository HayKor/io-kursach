package ru.haykor.sudv.client.ui.main

import ru.haykor.sudv.shared.domain.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals

class SectionTest {
    @Test
    fun sectionsFollowThePermissionMatrix() {
        assertEquals(listOf(Section.USERS, Section.EMPLOYEES), sectionsFor(UserRole.ADMIN))
        assertEquals(listOf(Section.PENALTIES, Section.EMPLOYEES, Section.REPORTS), sectionsFor(UserRole.HR))
        assertEquals(listOf(Section.PENALTIES, Section.EMPLOYEES), sectionsFor(UserRole.MANAGER))
    }
}
