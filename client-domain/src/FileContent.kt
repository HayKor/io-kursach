package ru.haykor.sudv.client.domain

/** A file sent to or received from the server: attachments, exports and archived reports. */
class FileContent(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
)
