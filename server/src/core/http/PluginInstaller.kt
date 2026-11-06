package ru.haykor.sudv.server.core.http

import io.ktor.server.application.Application

/**
 * A Ktor plugin set up at startup; implementations contribute themselves with
 * `@ContributesIntoSet(AppScope::class)` and are installed in [PluginOrder] order, before any route is mounted.
 */
interface PluginInstaller {
    val order: PluginOrder
    fun Application.install()
}

/** Installation order is declaration order. */
enum class PluginOrder {
    // First, so every call is logged, including the ones that fail in later plugins.
    CALL_LOGGING,
    CONTENT_NEGOTIATION,
    STATUS_PAGES,
    AUTHENTICATION,
    EXPIRY_SCHEDULER,
}
