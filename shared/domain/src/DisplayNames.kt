package ru.haykor.sudv.shared.domain

val PenaltyType.displayName: String
    get() = when (this) {
        PenaltyType.REMARK -> "Замечание"
        PenaltyType.REPRIMAND -> "Выговор"
    }

val PenaltyStatus.displayName: String
    get() = when (this) {
        PenaltyStatus.ACTIVE -> "Действует"
        PenaltyStatus.EXPIRED -> "Погашено"
        PenaltyStatus.REMOVED -> "Снято досрочно"
    }

val UserRole.displayName: String
    get() = when (this) {
        UserRole.ADMIN -> "Администратор"
        UserRole.HR -> "Кадровик"
        UserRole.MANAGER -> "Руководитель"
    }
