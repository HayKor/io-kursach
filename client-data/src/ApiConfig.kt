package ru.haykor.sudv.client.data

/**
 * [baseUrl] is the server's origin, e.g. `http://10.0.2.2:8080`; request paths come from `ApiRoutes`.
 * Each entry point (`android-app`, `web-app`) supplies it, so one build of `client-data` serves any server.
 */
class ApiConfig(val baseUrl: String) {
    companion object {
        /** A blank [configured] value falls back to [fallback], e.g. the web page's own origin. */
        fun of(configured: String?, fallback: String): ApiConfig =
            ApiConfig(configured?.trim()?.removeSuffix("/")?.takeIf { it.isNotEmpty() } ?: fallback)
    }
}
