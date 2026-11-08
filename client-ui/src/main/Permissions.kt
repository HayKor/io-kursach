package ru.haykor.sudv.client.ui.main

import ru.haykor.sudv.shared.domain.UserRole

// What the UI offers each role; mirrors the server's permission matrix, which still checks every request itself.

/** ADMIN too: an account needs an employee, so the first HR account could not be created otherwise. */
val UserRole.canCreateEmployees: Boolean get() = this == UserRole.ADMIN || this == UserRole.HR

val UserRole.canEditEmployees: Boolean get() = this == UserRole.HR

/** MANAGER sees only their own department, so the department filter is pointless for them. */
val UserRole.seesAllDepartments: Boolean get() = this != UserRole.MANAGER

/** Registering and early removal; MANAGER only reads penalties of their department. */
val UserRole.canManagePenalties: Boolean get() = this == UserRole.HR

/** Penalties, attachments and personal cards; ADMIN manages accounts and employees only. */
val UserRole.canSeePenalties: Boolean get() = this == UserRole.HR || this == UserRole.MANAGER

/** The summary report and its archive. */
val UserRole.canUseReports: Boolean get() = this == UserRole.HR
