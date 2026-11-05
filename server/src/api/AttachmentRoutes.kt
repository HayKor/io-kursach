package ru.haykor.sudv.api

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.server.application.call
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import ru.haykor.sudv.config.AppConfig
import ru.haykor.sudv.contract.ApiParams
import ru.haykor.sudv.contract.ApiRoutes
import ru.haykor.sudv.contract.ErrorCode
import ru.haykor.sudv.domain.AttachmentId
import ru.haykor.sudv.domain.PenaltyId
import ru.haykor.sudv.domain.UserRole
import ru.haykor.sudv.records.IRecordService

// Exceeding receiveMultipart's formFieldLimit (which applies to file parts too) ends the call with a plain 500
// that route code cannot intercept in Ktor 3.6.1, so it is only a backstop. The user-facing limit is enforced by
// the Content-Length precheck and a bounded read of the file part.
private const val MULTIPART_HARD_LIMIT_BYTES = 64L * 1024 * 1024

// Room for multipart boundaries and part headers on top of the file itself.
private const val MULTIPART_OVERHEAD_BYTES = 64L * 1024

@Inject
@ContributesIntoSet(AppScope::class)
class AttachmentRoutes(private val recordService: IRecordService, appConfig: AppConfig) : RouteRegistrar {
    private val maxAttachmentBytes = appConfig.maxAttachmentBytes
    private val formFieldLimit = maxOf(MULTIPART_HARD_LIMIT_BYTES, maxAttachmentBytes + 1024 * 1024)

    override fun Route.register() {
        authenticatedApi {
            /** Tag: Attachments */
            post(ApiRoutes.PENALTY_ATTACHMENTS) {
                call.requireRole(UserRole.HR)
                val penaltyId = PenaltyId(call.pathIdValue())
                val contentLength = call.request.contentLength()
                if (contentLength != null && contentLength > maxAttachmentBytes + MULTIPART_OVERHEAD_BYTES) {
                    throw ApiException(ErrorCode.FILE_TOO_LARGE, "Файл больше допустимого размера")
                }

                var fileName: String? = null
                var contentType: String? = null
                var bytes: ByteArray? = null
                var fileParts = 0

                val multipart = call.receiveMultipart(formFieldLimit = formFieldLimit)
                while (true) {
                    val part = multipart.readPart() ?: break
                    try {
                        if (part is PartData.FileItem && part.name == ApiParams.FILE) {
                            fileParts++
                            if (fileParts == 1) {
                                fileName = part.originalFileName
                                contentType = part.contentType?.withoutParameters()?.toString()
                                // One byte over the limit is enough for IFileStorage.save to reject the file.
                                bytes = part.provider().readBuffer(maxAttachmentBytes + 1).readByteArray()
                            }
                        }
                    } finally {
                        part.release()
                    }
                    // Stop before the parser walks the rest of an oversized body into formFieldLimit.
                    if ((bytes?.size ?: 0) > maxAttachmentBytes) break
                }

                if (fileParts != 1) throw ApiException(ErrorCode.VALIDATION_FAILED, "Файл не передан")
                val safeName = (fileName ?: "").sanitizeFileName()
                if (safeName.isBlank()) throw ApiException(ErrorCode.VALIDATION_FAILED, "Имя файла не должно быть пустым")
                val type = contentType ?: throw ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE, "Допустимы только файлы PDF и JPEG")

                val dto = recordService.addAttachment(penaltyId, safeName, type, bytes ?: ByteArray(0))
                call.respond(HttpStatusCode.Created, dto)
            }

            /** Tag: Attachments */
            get(ApiRoutes.ATTACHMENT) {
                val actor = call.requireRole(UserRole.HR, UserRole.MANAGER)
                val id = AttachmentId(call.pathIdValue())
                val file = recordService.getAttachment(actor, id)
                call.response.header(HttpHeaders.ContentDisposition, attachmentDisposition(file.fileName))
                call.respondBytes(file.bytes, ContentType.parse(file.contentType))
            }

            /** Tag: Attachments */
            delete(ApiRoutes.ATTACHMENT) {
                call.requireRole(UserRole.HR)
                val id = AttachmentId(call.pathIdValue())
                recordService.deleteAttachment(id)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

/** Strips any directory part of a client-supplied file name, trims it and caps it at 255 characters. */
private fun String.sanitizeFileName(): String {
    val afterLastSeparator = substring(maxOf(lastIndexOf('/'), lastIndexOf('\\')) + 1)
    return afterLastSeparator.trim().take(255)
}
