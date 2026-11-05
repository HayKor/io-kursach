package ru.haykor.sudv

/** Вид дисциплинарного взыскания. */
enum class PenaltyType {
    /** Замечание. */
    REMARK,

    /** Выговор. */
    REPRIMAND,
}

/** Статус взыскания. */
enum class PenaltyStatus {
    /** «Действует». */
    ACTIVE,

    /** «Погашено» — истёк год с даты приказа. */
    EXPIRED,

    /** «Снято досрочно» — по приказу о снятии. */
    REMOVED,
}
