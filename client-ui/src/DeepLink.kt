package ru.haykor.sudv.client.ui

/**
 * Path segments of the URL the web client was opened with, e.g. `http://host/penalties/42?x=1` → `[penalties, 42]`.
 * Each navigation level takes its own leading segment and hands the rest down. Android has none.
 */
data class DeepLink(val segments: List<String>) {
    val first: String? get() = segments.firstOrNull()

    fun dropFirst(): DeepLink = DeepLink(segments.drop(1))

    companion object {
        val None = DeepLink(emptyList())

        fun parse(url: String?): DeepLink {
            if (url.isNullOrBlank()) return None
            val path = if ("://" in url) url.substringAfter("://").substringAfter('/', "") else url
            return DeepLink(
                path.substringBefore('?').substringBefore('#').split('/').filter { it.isNotEmpty() },
            )
        }
    }
}
