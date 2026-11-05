package ru.haykor.sudv

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(modelRepository: ModelRepository) {
    routing {
        get("/") {
            val modelDto = ModelDto(id = 1)
            call.respond(modelDto)
        }
        get("/models") {
            call.respond(modelRepository.findAll().map { ModelDto(id = it.id) })
        }
    }
}
