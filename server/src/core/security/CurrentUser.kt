package ru.haykor.sudv.server.core.security

import ru.haykor.sudv.shared.domain.DepartmentId
import ru.haykor.sudv.shared.domain.UserId
import ru.haykor.sudv.shared.domain.UserRole

/** Authentication principal attached to a call by the `jwt` provider. Department scoping for MANAGER is enforced by services, not here. */
data class CurrentUser(val id: UserId, val role: UserRole, val departmentId: DepartmentId)
