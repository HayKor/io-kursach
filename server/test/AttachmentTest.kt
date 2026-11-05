package ru.haykor.sudv

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import ru.haykor.sudv.config.AppConfig
import ru.haykor.sudv.contract.ApiParams
import ru.haykor.sudv.contract.ApiRoutes
import ru.haykor.sudv.contract.AttachmentDto
import ru.haykor.sudv.contract.ErrorCode
import ru.haykor.sudv.contract.ErrorResponse
import ru.haykor.sudv.contract.PenaltyDetailsDto
import ru.haykor.sudv.domain.AttachmentId
import ru.haykor.sudv.domain.PenaltyId
import ru.haykor.sudv.domain.UserRole
import ru.haykor.sudv.files.LocalFileStorage
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val MINIMAL_PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".encodeToByteArray()
private val MINIMAL_JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(16)
private val MINIMAL_PNG = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()) + ByteArray(16)

private suspend fun ApplicationTestBuilder.uploadAttachment(
    token: String,
    penaltyId: PenaltyId,
    fileName: String,
    contentType: String,
    bytes: ByteArray,
): HttpResponse {
    val parts = formData {
        append(
            ApiParams.FILE,
            bytes,
            Headers.build {
                append(HttpHeaders.ContentType, contentType)
                // HTTP quoted-strings use backslash as an escape character; a raw '\' or '"' in the file
                // name (e.g. a Windows path used in a test) must be escaped or it is dropped on parsing.
                val escapedFileName = fileName.replace("\\", "\\\\").replace("\"", "\\\"")
                append(HttpHeaders.ContentDisposition, "filename=\"$escapedFileName\"")
            },
        )
    }
    return api().submitFormWithBinaryData(ApiRoutes.penaltyAttachments(penaltyId), parts) { bearer(token) }
}

private suspend fun ApplicationTestBuilder.downloadAttachment(token: String, id: AttachmentId): HttpResponse =
    api().get(ApiRoutes.attachment(id)) { bearer(token) }

private suspend fun ApplicationTestBuilder.deleteAttachment(token: String, id: AttachmentId): HttpResponse =
    api().delete(ApiRoutes.attachment(id)) { bearer(token) }

// Decoded with java.net.URLDecoder, independently of the server's own encoder.
private fun decodedFileName(contentDisposition: String): String {
    val disposition = ContentDisposition.parse(contentDisposition)
    val encoded = disposition.parameter(ContentDisposition.Parameters.FileNameAsterisk)
        ?: error("No filename* parameter in: $contentDisposition")
    return java.net.URLDecoder.decode(encoded.removePrefix("UTF-8''"), Charsets.UTF_8)
}

class AttachmentTest {
    @Test
    fun uploadingPdfAndJpegSucceedsAndBothAppearInPenaltyDetails() = apiTest {
        val hr = createUser(UserRole.HR)
        val penalty = createPenalty(createEmployee(), hrToken = hr)

        val pdfResponse = uploadAttachment(hr, penalty.id, "Приказ.pdf", "application/pdf", MINIMAL_PDF)
        assertEquals(HttpStatusCode.Created, pdfResponse.status)
        val pdfDto = pdfResponse.body<AttachmentDto>()
        assertEquals("Приказ.pdf", pdfDto.fileName)
        assertEquals("application/pdf", pdfDto.contentType)
        assertEquals(MINIMAL_PDF.size.toLong(), pdfDto.sizeBytes)

        val jpegResponse = uploadAttachment(hr, penalty.id, "Фото.jpg", "image/jpeg", MINIMAL_JPEG)
        assertEquals(HttpStatusCode.Created, jpegResponse.status)
        val jpegDto = jpegResponse.body<AttachmentDto>()
        assertEquals("image/jpeg", jpegDto.contentType)

        val details = api().get(ApiRoutes.penalty(penalty.id)) { bearer(hr) }.body<PenaltyDetailsDto>()
        assertEquals(setOf(pdfDto.id, jpegDto.id), details.attachments.map { it.id }.toSet())
    }

    @Test
    fun downloadReturnsBytesContentTypeAndADecodableCyrillicFileName() = apiTest {
        val hr = createUser(UserRole.HR)
        val penalty = createPenalty(createEmployee(), hrToken = hr)
        val originalName = "Акт №1.pdf"

        val uploaded = uploadAttachment(hr, penalty.id, originalName, "application/pdf", MINIMAL_PDF).body<AttachmentDto>()
        assertEquals(originalName, uploaded.fileName)

        val download = downloadAttachment(hr, uploaded.id)
        assertEquals(HttpStatusCode.OK, download.status)
        assertEquals(MINIMAL_PDF.toList(), download.bodyAsBytes().toList())
        assertTrue(download.contentType().toString().startsWith("application/pdf"))

        val header = download.headers[HttpHeaders.ContentDisposition] ?: error("Missing Content-Disposition header")
        assertEquals(originalName, decodedFileName(header))
    }

    @Test
    fun pngIsRejectedEvenWhenDeclaredAsJpeg() = apiTest {
        val hr = createUser(UserRole.HR)
        val penalty = createPenalty(createEmployee(), hrToken = hr)

        val declaredAsPng = uploadAttachment(hr, penalty.id, "image.png", "image/png", MINIMAL_PNG)
        assertEquals(HttpStatusCode.UnsupportedMediaType, declaredAsPng.status)
        assertEquals(ErrorCode.UNSUPPORTED_FILE_TYPE, declaredAsPng.body<ErrorResponse>().code)

        val declaredAsJpeg = uploadAttachment(hr, penalty.id, "image.jpg", "image/jpeg", MINIMAL_PNG)
        assertEquals(HttpStatusCode.UnsupportedMediaType, declaredAsJpeg.status)
        assertEquals(ErrorCode.UNSUPPORTED_FILE_TYPE, declaredAsJpeg.body<ErrorResponse>().code)
    }

    @Test
    fun emptyFileIsRejectedAsTooLarge() = apiTest {
        val hr = createUser(UserRole.HR)
        val penalty = createPenalty(createEmployee(), hrToken = hr)

        val response = uploadAttachment(hr, penalty.id, "empty.pdf", "application/pdf", ByteArray(0))
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertEquals(ErrorCode.FILE_TOO_LARGE, response.body<ErrorResponse>().code)
    }

    @Test
    fun fileOverTheConfiguredLimitIsRejectedAsTooLarge() = apiTest(config = mapOf("files.maxAttachmentBytes" to "1024")) {
        val hr = createUser(UserRole.HR)
        val penalty = createPenalty(createEmployee(), hrToken = hr)
        val oversized = MINIMAL_PDF + ByteArray(2048)

        val response = uploadAttachment(hr, penalty.id, "large.pdf", "application/pdf", oversized)
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertEquals(ErrorCode.FILE_TOO_LARGE, response.body<ErrorResponse>().code)
    }

    @Test
    fun bodyFarOverTheLimitIsRejectedByContentLength() = apiTest(config = mapOf("files.maxAttachmentBytes" to "1024")) {
        val hr = createUser(UserRole.HR)
        val penalty = createPenalty(createEmployee(), hrToken = hr)
        val oversized = MINIMAL_PDF + ByteArray(1024 * 1024)

        val response = uploadAttachment(hr, penalty.id, "huge.pdf", "application/pdf", oversized)
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertEquals(ErrorCode.FILE_TOO_LARGE, response.body<ErrorResponse>().code)
    }

    @Test
    fun fileNameWithAPathIsSanitizedToItsLastSegment() = apiTest {
        val hr = createUser(UserRole.HR)
        val penalty = createPenalty(createEmployee(), hrToken = hr)

        val unixPath = uploadAttachment(hr, penalty.id, "../../etc/passwd.pdf", "application/pdf", MINIMAL_PDF).body<AttachmentDto>()
        assertEquals("passwd.pdf", unixPath.fileName)

        val windowsPath = uploadAttachment(hr, penalty.id, "C:\\tmp\\a.pdf", "application/pdf", MINIMAL_PDF).body<AttachmentDto>()
        assertEquals("a.pdf", windowsPath.fileName)
    }

    @Test
    fun deletingAnAttachmentRemovesTheFileAndFurtherAccessIsNotFound() = apiTest {
        val hr = createUser(UserRole.HR)
        val penalty = createPenalty(createEmployee(), hrToken = hr)
        val uploaded = uploadAttachment(hr, penalty.id, "doc.pdf", "application/pdf", MINIMAL_PDF).body<AttachmentDto>()

        val storageKey = appGraph().penaltyRepository.findAttachment(uploaded.id)?.storageKey ?: error("Attachment not found")
        val storedFile = java.nio.file.Path.of(appGraph().appConfig.filesDir).resolve(storageKey)
        assertTrue(Files.exists(storedFile))

        val deleteResponse = deleteAttachment(hr, uploaded.id)
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
        assertFalse(Files.exists(storedFile))

        assertEquals(HttpStatusCode.NotFound, downloadAttachment(hr, uploaded.id).status)
        assertEquals(HttpStatusCode.NotFound, deleteAttachment(hr, uploaded.id).status)
    }

    @Test
    fun managerAccessIsScopedToTheirOwnDepartment() = apiTest {
        val hr = createUser(UserRole.HR)
        val managerToken = createUser(UserRole.MANAGER, departmentName = "ИТ-отдел")

        val ownEmployee = createEmployee(personnelNumber = "ATT-OWN", departmentName = "ИТ-отдел")
        val otherEmployee = createEmployee(personnelNumber = "ATT-OTHER", departmentName = "Бухгалтерия")
        val ownPenalty = createPenalty(ownEmployee, hrToken = hr)
        val otherPenalty = createPenalty(otherEmployee, hrToken = hr)

        val ownAttachment = uploadAttachment(hr, ownPenalty.id, "own.pdf", "application/pdf", MINIMAL_PDF).body<AttachmentDto>()
        val otherAttachment = uploadAttachment(hr, otherPenalty.id, "other.pdf", "application/pdf", MINIMAL_PDF).body<AttachmentDto>()

        assertEquals(HttpStatusCode.OK, downloadAttachment(managerToken, ownAttachment.id).status)
        assertEquals(HttpStatusCode.Forbidden, downloadAttachment(managerToken, otherAttachment.id).status)

        val uploadAttempt = uploadAttachment(managerToken, ownPenalty.id, "x.pdf", "application/pdf", MINIMAL_PDF)
        assertEquals(HttpStatusCode.Forbidden, uploadAttempt.status)

        assertEquals(HttpStatusCode.Forbidden, deleteAttachment(managerToken, ownAttachment.id).status)
    }

    @Test
    fun uploadingToAnUnknownPenaltyIsNotFound() = apiTest {
        val hr = createUser(UserRole.HR)
        val response = uploadAttachment(hr, PenaltyId(999_999_999), "doc.pdf", "application/pdf", MINIMAL_PDF)
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ErrorCode.NOT_FOUND, response.body<ErrorResponse>().code)
    }
}

class LocalFileStorageTest {
    @Test
    fun readRejectsANonUuidKeyAsPathTraversalDefence() = runBlocking<Unit> {
        val storage = LocalFileStorage(testFileStorageConfig())
        assertFailsWith<IllegalArgumentException> { storage.read("../../etc/passwd") }
    }

    @Test
    fun deleteRejectsANonUuidKeyAsPathTraversalDefence() = runBlocking<Unit> {
        val storage = LocalFileStorage(testFileStorageConfig())
        assertFailsWith<IllegalArgumentException> { storage.delete("not-a-uuid") }
    }

    private fun testFileStorageConfig(): AppConfig = AppConfig(
        timeZone = TimeZone.UTC,
        jwtSecret = "test-secret",
        jwtIssuer = "sudv-test",
        jwtTtlHours = 12,
        passwordHashIterations = 1000,
        filesDir = createTempDirectory("sudv-filestorage-test").toString(),
        maxAttachmentBytes = 1024,
        expirySchedulerEnabled = false,
        expiryRunAt = LocalTime(0, 5),
        bootstrapDepartments = emptyList(),
        adminLogin = "admin",
        adminPassword = "admin12345",
        adminDepartment = "ИТ-отдел",
    )
}
