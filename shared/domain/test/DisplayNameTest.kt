package ru.haykor.sudv.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayNameTest {
    @Test
    fun penaltyTypeDisplayNames() {
        assertEquals("Замечание", PenaltyType.REMARK.displayName)
        assertEquals("Выговор", PenaltyType.REPRIMAND.displayName)
    }

    @Test
    fun penaltyStatusDisplayNames() {
        assertEquals("Действует", PenaltyStatus.ACTIVE.displayName)
        assertEquals("Погашено", PenaltyStatus.EXPIRED.displayName)
        assertEquals("Снято досрочно", PenaltyStatus.REMOVED.displayName)
    }

    @Test
    fun userRoleDisplayNames() {
        assertEquals("Администратор", UserRole.ADMIN.displayName)
        assertEquals("Кадровик", UserRole.HR.displayName)
        assertEquals("Руководитель", UserRole.MANAGER.displayName)
    }
}
