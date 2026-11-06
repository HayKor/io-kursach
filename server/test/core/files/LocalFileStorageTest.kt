package ru.haykor.sudv.server.core.files

import io.ktor.client.request.delete
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import ru.haykor.sudv.server.core.config.AppConfig
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFailsWith

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
        corsAllowedOrigins = emptyList(),
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
