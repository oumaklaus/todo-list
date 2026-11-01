package com.example.taskflow.routes

import com.example.taskflow.auth.JwtTokenService
import com.example.taskflow.auth.PasswordHasher
import com.example.taskflow.model.AuthResponse
import com.example.taskflow.model.ErrorResponse
import com.example.taskflow.model.LoginRequest
import com.example.taskflow.model.RegisterRequest
import com.example.taskflow.model.toAuthUser
import com.example.taskflow.repository.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.authRoutes(
    users: UserRepository,
    passwordHasher: PasswordHasher,
    tokens: JwtTokenService
) {
    val dummyHash = passwordHasher.hash("not-a-real-user-password")

    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>().normalized()
            request.validationError()?.let { error ->
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(error))
                return@post
            }

            val created = users.create(
                displayName = request.displayName,
                email = request.email,
                passwordHash = passwordHasher.hash(request.password)
            )
            if (created == null) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("An account with that email already exists")
                )
                return@post
            }

            call.respond(
                HttpStatusCode.Created,
                AuthResponse(tokens.issue(created), created.toAuthUser())
            )
        }

        post("/login") {
            val request = call.receive<LoginRequest>().normalized()
            val user = users.findByEmail(request.email)
            val hashToCheck = user?.passwordHash ?: dummyHash
            val validLength = request.password.isValidPasswordLength()
            val passwordToCheck = if (validLength) request.password else "invalid-login-password"
            val validPassword = passwordHasher.verify(passwordToCheck, hashToCheck) && validLength
            if (user == null || !validPassword) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("Invalid email or password")
                )
                return@post
            }

            call.respond(AuthResponse(tokens.issue(user), user.toAuthUser()))
        }
    }
}

private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

private fun RegisterRequest.normalized(): RegisterRequest = copy(
    displayName = displayName.trim(),
    email = email.trim().lowercase()
)

private fun LoginRequest.normalized(): LoginRequest = copy(email = email.trim().lowercase())

private fun RegisterRequest.validationError(): String? = when {
    displayName.isBlank() -> "Display name is required"
    displayName.length > 60 -> "Display name must be 60 characters or fewer"
    email.length > 254 || !emailPattern.matches(email) -> "Enter a valid email address"
    password.length < 8 -> "Password must be at least 8 characters"
    password.length > 72 || password.toByteArray(Charsets.UTF_8).size > 72 ->
        "Password must be 72 characters or fewer"
    else -> null
}

private fun String.isValidPasswordLength(): Boolean =
    length in 8..72 && toByteArray(Charsets.UTF_8).size <= 72
