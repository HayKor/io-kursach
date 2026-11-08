package ru.haykor.sudv.client.ui.penalties

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import ru.haykor.sudv.client.domain.ApiException
import ru.haykor.sudv.client.ui.DeepLink
import ru.haykor.sudv.client.ui.FakeApiClient
import ru.haykor.sudv.client.ui.PDF_BYTES
import ru.haykor.sudv.client.ui.attachment
import ru.haykor.sudv.client.ui.common.FileAction
import ru.haykor.sudv.client.ui.common.PickedFile
import ru.haykor.sudv.client.ui.penalty
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.contract.PenaltyDetailsDto
import ru.haykor.sudv.shared.domain.AttachmentId
import ru.haykor.sudv.shared.domain.AttachmentRules
import ru.haykor.sudv.shared.domain.PenaltyId
import ru.haykor.sudv.shared.domain.UserRole
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentsComponentTest {
    private val api = FakeApiClient(signedIn = true).apply {
        onPenalty = { PenaltyDetailsDto(penalty(it.value), listOf(attachment(5, "Объяснительная.pdf")), emptyList()) }
    }

    /** How many times a picked file's content was read. */
    private var reads = 0

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // Attachments live in the penalty card; the card is opened through the section, as in the app.
    private fun TestScope.attachments(role: UserRole = UserRole.HR): AttachmentsComponent {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val section = DefaultPenaltiesComponent(DefaultComponentContext(LifecycleRegistry()), api, role, DeepLink.parse("1"))
        return (section.stack.value.active.instance as PenaltiesComponent.Child.Details).component.attachments
    }

    private fun file(name: String, bytes: ByteArray = PDF_BYTES, size: Long = bytes.size.toLong()) =
        PickedFile(name, size) { reads++; bytes }

    @Test
    fun cardShowsTheAttachmentsOfThePenalty() = runTest {
        val state = attachments().state.value

        assertEquals(listOf(AttachmentId(5)), state.attachments.map { it.id })
        assertTrue(state.canEdit)
    }

    @Test
    fun fileOfAnotherTypeIsNeitherReadNorSent() = runTest {
        val attachments = attachments()

        attachments.onFilePicked(file("photo.png"))

        assertEquals("Допустимы только файлы PDF и JPEG", attachments.state.value.error)
        assertEquals(0, reads)
        assertTrue(api.uploads.isEmpty())
    }

    @Test
    fun fileOverTheLimitIsNeitherReadNorSent() = runTest {
        val attachments = attachments()

        attachments.onFilePicked(file("scan.pdf", size = AttachmentRules.MAX_BYTES + 1))

        assertEquals("Файл больше 10,0 МБ", attachments.state.value.error)
        assertEquals(0, reads)
        assertTrue(api.uploads.isEmpty())
    }

    @Test
    fun renamedFileIsCaughtByItsContent() = runTest {
        val attachments = attachments()
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

        attachments.onFilePicked(file("photo.jpg", png))

        assertEquals("Допустимы только файлы PDF и JPEG", attachments.state.value.error)
        assertFalse(attachments.state.value.isUploading)
        assertTrue(api.uploads.isEmpty())
    }

    @Test
    fun validFileIsSentWithItsTypeAndAdded() = runTest {
        val attachments = attachments()

        attachments.onFilePicked(file("Приказ №5.PDF"))

        val (penaltyId, sent) = api.uploads.single()
        assertEquals(PenaltyId(1), penaltyId)
        assertEquals("Приказ №5.PDF", sent.fileName)
        assertEquals("application/pdf", sent.contentType)
        assertContentEquals(PDF_BYTES, sent.bytes)
        assertEquals(listOf(AttachmentId(5), AttachmentId(90)), attachments.state.value.attachments.map { it.id })
        assertNull(attachments.state.value.error)
    }

    @Test
    fun serverRefusalIsShown() = runTest {
        val attachments = attachments()
        api.onUpload = { _, _ -> throw ApiException(ErrorCode.FILE_TOO_LARGE, "Файл больше допустимого размера (5 МБ)") }

        attachments.onFilePicked(file("scan.pdf"))
        assertEquals("Файл больше допустимого размера (5 МБ)", attachments.state.value.error)

        api.onUpload = { _, _ -> throw ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE, "Допустимы только файлы PDF и JPEG") }
        attachments.onFilePicked(file("scan.pdf"))
        assertEquals("Допустимы только файлы PDF и JPEG", attachments.state.value.error)
        assertFalse(attachments.state.value.isUploading)
        assertEquals(listOf(AttachmentId(5)), attachments.state.value.attachments.map { it.id })
    }

    @Test
    fun managerOnlyDownloads() = runTest {
        val attachments = attachments(UserRole.MANAGER)

        attachments.onFilePicked(file("scan.pdf"))
        attachments.onDeleteClick(AttachmentId(5))

        assertFalse(attachments.state.value.canEdit)
        assertTrue(api.uploads.isEmpty())
        assertNull(attachments.state.value.confirmingDelete)

        attachments.onDownloadClick(AttachmentId(5), FileAction.SAVE)
        assertEquals(listOf(AttachmentId(5)), api.downloads)
    }

    @Test
    fun downloadedFileWaitsForThePlatformOnce() = runTest {
        val attachments = attachments()

        attachments.onDownloadClick(AttachmentId(5), FileAction.OPEN)

        val delivery = attachments.state.value.delivery!!
        assertEquals("Приказ.pdf", delivery.file.fileName)
        assertEquals(FileAction.OPEN, delivery.action)
        assertNull(attachments.state.value.busyId)

        attachments.onDelivered()
        assertNull(attachments.state.value.delivery)
    }

    @Test
    fun failedDownloadIsShown() = runTest {
        val attachments = attachments()
        api.onDownload = { throw ApiException(ErrorCode.NOT_FOUND, "Файл вложения не найден") }

        attachments.onDownloadClick(AttachmentId(5), FileAction.SAVE)

        assertEquals("Файл вложения не найден", attachments.state.value.error)
        assertNull(attachments.state.value.delivery)
    }

    @Test
    fun deletionAsksFirst() = runTest {
        val attachments = attachments()

        attachments.onDeleteClick(AttachmentId(5))
        assertEquals(AttachmentId(5), attachments.state.value.confirmingDelete?.id)
        attachments.onDeleteDismiss()
        assertTrue(api.deletedAttachments.isEmpty())

        attachments.onDeleteClick(AttachmentId(5))
        attachments.onDeleteConfirm()

        assertEquals(listOf(AttachmentId(5)), api.deletedAttachments)
        assertTrue(attachments.state.value.attachments.isEmpty())
    }
}
