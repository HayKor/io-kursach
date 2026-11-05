package ru.haykor.sudv.server.core.config

import io.ktor.server.config.ApplicationConfig
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

/** Typed view of `application.yaml`; built once from [ApplicationConfig]. */
data class AppConfig(
    val timeZone: TimeZone,
    val jwtSecret: String,
    val jwtIssuer: String,
    val jwtTtlHours: Long,
    val passwordHashIterations: Int,
    val filesDir: String,
    val maxAttachmentBytes: Long,
    val expirySchedulerEnabled: Boolean,
    val expiryRunAt: LocalTime,
    val bootstrapDepartments: List<String>,
    val adminLogin: String,
    val adminPassword: String,
    val adminDepartment: String,
) {
    companion object {
        fun from(config: ApplicationConfig): AppConfig {
            val app = config.config("app")
            val security = config.config("security")
            val jwt = security.config("jwt")
            val files = config.config("files")
            val expiry = config.config("expiry")
            val bootstrap = config.config("bootstrap")
            val admin = bootstrap.config("admin")

            return AppConfig(
                timeZone = TimeZone.of(app.property("timeZone").getString()),
                jwtSecret = jwt.property("secret").getString(),
                jwtIssuer = jwt.property("issuer").getString(),
                jwtTtlHours = jwt.property("ttlHours").getString().toLong(),
                passwordHashIterations = security.property("passwordHashIterations").getString().toInt(),
                filesDir = files.property("dir").getString(),
                maxAttachmentBytes = files.property("maxAttachmentBytes").getString().toLong(),
                expirySchedulerEnabled = expiry.property("schedulerEnabled").getString().toBoolean(),
                expiryRunAt = LocalTime.parse(expiry.property("runAt").getString()),
                bootstrapDepartments = bootstrap.property("departments").getList(),
                adminLogin = admin.property("login").getString(),
                adminPassword = admin.property("password").getString(),
                adminDepartment = admin.property("department").getString(),
            )
        }
    }
}
