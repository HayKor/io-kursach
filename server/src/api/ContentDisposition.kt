package ru.haykor.sudv.api

// RFC 3986 unreserved, a subset of RFC 5987 attr-char: everything else is percent-encoded.
private val RFC5987_UNRESERVED = (('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '.', '_', '~')).toSet()

/** Hand-built because Ktor 3.6.1's `FileNameAsterisk` encoder leaves non-ASCII characters unencoded. */
fun attachmentDisposition(fileName: String): String {
    val encoded = buildString {
        for (byte in fileName.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt() and 0xFF
            val char = c.toChar()
            if (char in RFC5987_UNRESERVED) append(char) else append("%%%02X".format(c))
        }
    }
    return "attachment; filename*=UTF-8''$encoded"
}
