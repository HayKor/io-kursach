package ru.haykor.sudv.api

import io.ktor.server.routing.Route

/**
 * A group of routes mounted on the routing root; implementations contribute themselves with
 * `@ContributesIntoSet(AppScope::class)`, so adding a group needs no change to `AppGraph` or `module()`.
 *
 * The Swagger `Tag:` KDoc goes on every endpoint: the Ktor compiler plugin ignores it on `authenticate`, and
 * `route("")` returns the parent route itself, so a tag there would leak onto every other group.
 */
fun interface RouteRegistrar {
    fun Route.register()
}
