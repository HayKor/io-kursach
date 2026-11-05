package ru.haykor.sudv.domain

object AttachmentRules {
    const val MAX_BYTES: Long = 10L * 1024 * 1024
    val allowedContentTypes: Set<String> = setOf("application/pdf", "image/jpeg")

    private val pdfSignature = byteArrayOf(0x25, 0x50, 0x44, 0x46) // "%PDF"
    private val jpegSignature = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

    // Checked against the declared content type, not guessed from bytes alone,
    // so a renamed file with a mismatched extension is still rejected.
    fun matchesSignature(contentType: String, bytes: ByteArray): Boolean = when (contentType) {
        "application/pdf" -> bytes.startsWith(pdfSignature)
        "image/jpeg" -> bytes.startsWith(jpegSignature)
        else -> false
    }

    private fun ByteArray.startsWith(signature: ByteArray): Boolean {
        if (size < signature.size) return false
        for (i in signature.indices) {
            if (this[i] != signature[i]) return false
        }
        return true
    }
}
