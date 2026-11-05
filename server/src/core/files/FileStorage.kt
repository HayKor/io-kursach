package ru.haykor.sudv.server.core.files

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.haykor.sudv.server.core.config.AppConfig
import ru.haykor.sudv.server.core.error.ApiException
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.domain.AttachmentRules
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

interface IFileStorage {
    /** Returns the storage key (a random UUID) the file was written under. [validateAttachment] applies [AttachmentRules]. */
    suspend fun save(contentType: String, bytes: ByteArray, validateAttachment: Boolean): String

    /** Returns `null` if no file exists under [key]. */
    suspend fun read(key: String): ByteArray?

    /** Idempotent: deleting an already-missing key is not an error. */
    suspend fun delete(key: String)
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class LocalFileStorage(private val appConfig: AppConfig) : IFileStorage {

    // Created once, at construction, rather than on every call.
    private val rootDir: Path = Path.of(appConfig.filesDir).also { Files.createDirectories(it) }

    override suspend fun save(contentType: String, bytes: ByteArray, validateAttachment: Boolean): String {
        if (validateAttachment) validateAttachment(contentType, bytes)

        val key = UUID.randomUUID().toString()
        withContext(Dispatchers.IO) {
            Files.write(rootDir.resolve(key), bytes, StandardOpenOption.CREATE_NEW)
        }
        return key
    }

    override suspend fun read(key: String): ByteArray? {
        val path = rootDir.resolve(key.requireStorageKey())
        return withContext(Dispatchers.IO) {
            if (Files.isRegularFile(path)) Files.readAllBytes(path) else null
        }
    }

    override suspend fun delete(key: String) {
        val path = rootDir.resolve(key.requireStorageKey())
        withContext(Dispatchers.IO) {
            Files.deleteIfExists(path)
        }
    }

    private fun validateAttachment(contentType: String, bytes: ByteArray) {
        if (bytes.isEmpty()) throw ApiException(ErrorCode.FILE_TOO_LARGE, "Файл пустой")
        if (bytes.size > appConfig.maxAttachmentBytes) {
            val maxMb = (appConfig.maxAttachmentBytes + BYTES_PER_MB - 1) / BYTES_PER_MB
            throw ApiException(ErrorCode.FILE_TOO_LARGE, "Файл больше допустимого размера ($maxMb МБ)")
        }
        if (contentType !in AttachmentRules.allowedContentTypes || !AttachmentRules.matchesSignature(contentType, bytes)) {
            throw ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE, "Допустимы только файлы PDF и JPEG")
        }
    }

    // Defence in depth: keys only ever come from the DB, but a malformed one must never resolve outside rootDir.
    private fun String.requireStorageKey(): String {
        val uuid = runCatching { UUID.fromString(this) }
            .getOrElse { throw IllegalArgumentException("Storage key is not a UUID: $this") }
        return uuid.toString()
    }

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L
    }
}
