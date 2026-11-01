package com.example.taskflow.routes

import com.example.taskflow.model.ErrorResponse
import com.example.taskflow.model.TaskInput
import com.example.taskflow.repository.TaskRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.taskRoutes(repository: TaskRepository) {
    route("/tasks") {
        get {
            call.respond(repository.findAll(call.ownerId()))
        }

        post {
            val input = call.receive<TaskInput>().normalized()
            val validationError = input.validationError()
            if (validationError != null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(validationError))
                return@post
            }

            call.respond(HttpStatusCode.Created, repository.create(call.ownerId(), input))
        }

        put("/{id}") {
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing task id"))
                return@put
            }

            val input = call.receive<TaskInput>().normalized()
            val validationError = input.validationError()
            if (validationError != null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(validationError))
                return@put
            }

            val updated = repository.update(call.ownerId(), id, input)
            if (updated == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Task not found"))
            } else {
                call.respond(updated)
            }
        }

        delete("/{id}") {
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing task id"))
                return@delete
            }

            if (repository.delete(call.ownerId(), id)) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Task not found"))
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.ownerId(): String =
    requireNotNull(principal<JWTPrincipal>()?.payload?.subject)

private fun TaskInput.normalized(): TaskInput = copy(
    title = title.trim(),
    notes = notes.trim(),
    category = category.trim()
)

private fun TaskInput.validationError(): String? = when {
    title.isBlank() -> "Title must not be empty"
    title.length > 120 -> "Title must be 120 characters or fewer"
    notes.length > 1000 -> "Notes must be 1000 characters or fewer"
    category.length > 40 -> "Category must be 40 characters or fewer"
    dueAt != null && dueAt <= 0 -> "Due date must be positive"
    else -> null
}
