package ru.haykor.sudv.shared.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttachmentRulesTest {
    private val pdfBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34) // "%PDF-1.4"
    private val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
    private val pngBytes = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    @Test
    fun pdfBytesMatchPdfContentType() {
        assertTrue(AttachmentRules.matchesSignature("application/pdf", pdfBytes))
    }

    @Test
    fun jpegBytesMatchJpegContentType() {
        assertTrue(AttachmentRules.matchesSignature("image/jpeg", jpegBytes))
    }

    @Test
    fun pngBytesDeclaredAsJpegDoNotMatch() {
        assertFalse(AttachmentRules.matchesSignature("image/jpeg", pngBytes))
    }

    @Test
    fun pngBytesDeclaredAsPdfDoNotMatch() {
        assertFalse(AttachmentRules.matchesSignature("application/pdf", pngBytes))
    }

    @Test
    fun unsupportedContentTypeNeverMatches() {
        assertFalse(AttachmentRules.matchesSignature("image/png", pngBytes))
    }
}
