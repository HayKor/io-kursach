package ru.haykor.sudv.server.core.http

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import ru.haykor.sudv.server.core.error.ApiException
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.contract.ErrorResponse

@Inject
@ContributesIntoSet(AppScope::class)
class StatusPagesPlugin : PluginInstaller {
    override val order = PluginOrder.STATUS_PAGES

    override fun Application.install() {
        install(StatusPages) {
            exception<ApiException> { call, cause ->
                call.respond(
                    HttpStatusCode.fromValue(cause.code.httpStatus),
                    ErrorResponse(cause.code, cause.message ?: ""),
                )
            }
            // Thrown by call.receive() on malformed JSON or an unknown enum value.
            exception<BadRequestException> { call, _ ->
                call.respond(
                    HttpStatusCode.fromValue(ErrorCode.VALIDATION_FAILED.httpStatus),
                    ErrorResponse(ErrorCode.VALIDATION_FAILED, "Некорректный запрос"),
                )
            }
            exception<ContentTransformationException> { call, _ ->
                call.respond(
                    HttpStatusCode.fromValue(ErrorCode.VALIDATION_FAILED.httpStatus),
                    ErrorResponse(ErrorCode.VALIDATION_FAILED, "Некорректный запрос"),
                )
            }
            exception<Throwable> { call, cause ->
                call.application.log.error("Unhandled exception", cause)
                call.respond(
                    HttpStatusCode.fromValue(ErrorCode.INTERNAL_ERROR.httpStatus),
                    ErrorResponse(ErrorCode.INTERNAL_ERROR, "Внутренняя ошибка сервера"),
                )
            }
        }
    }
}
