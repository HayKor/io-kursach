package ru.haykor.sudv.server.core.config

import io.ktor.server.config.ApplicationConfig
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

/** Typed view of `application.yaml`; built once from [ApplicationConfig]. */
data class AppConfig(
    val timeZone: TimeZone,
    val corsAllowedOrigins: List<String>,
    /** Built web client to serve on `/`; `null` when the server does not serve it. */
    val webDir: String?,
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
            val cors = config.config("cors")
            val web = config.config("web")
            val security = config.config("security")
            val jwt = security.config("jwt")
            val files = config.config("files")
            val expiry = config.config("expiry")
            val bootstrap = config.config("bootstrap")
            val admin = bootstrap.config("admin")

            return AppConfig(
                timeZone = TimeZone.of(app.property("timeZone").getString()),
                corsAllowedOrigins = cors.property("allowedOrigins").getString()
                    .split(",")
                    .map { it.trim().removeSuffix("/") }
                    .filter { it.isNotEmpty() },
                webDir = web.property("dir").getString().takeIf { it.isNotBlank() },
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
