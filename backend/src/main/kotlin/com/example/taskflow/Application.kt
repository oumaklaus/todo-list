package com.example.taskflow

import com.example.taskflow.auth.BcryptPasswordHasher
import com.example.taskflow.auth.JwtTokenService
import com.example.taskflow.model.ErrorResponse
import com.example.taskflow.model.HealthResponse
import com.example.taskflow.repository.MongoTaskRepository
import com.example.taskflow.repository.MongoUserRepository
import com.example.taskflow.repository.TaskRepository
import com.example.taskflow.repository.UserRepository
import com.example.taskflow.auth.PasswordHasher
import com.example.taskflow.routes.authRoutes
import com.example.taskflow.routes.taskRoutes
import com.mongodb.client.MongoClients
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

private const val LOCAL_JWT_SECRET = "taskflow-local-development-secret-value"

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull()
        ?: System.getenv("SERVER_PORT")?.toIntOrNull()
        ?: 8080
    val mongoUri = System.getenv("MONGODB_URI") ?: "mongodb://localhost:27017"
    val databaseName = System.getenv("MONGODB_DB") ?: "taskflow"
    val jwtSecret = System.getenv("JWT_SECRET") ?: LOCAL_JWT_SECRET

    val mongoClient = MongoClients.create(mongoUri)
    val database = mongoClient.getDatabase(databaseName)
    val taskRepository = MongoTaskRepository(database.getCollection("tasks"))
    val userRepository = MongoUserRepository(database.getCollection("users"))
    val tokenService = JwtTokenService(jwtSecret)

    Runtime.getRuntime().addShutdownHook(Thread { mongoClient.close() })

    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module(taskRepository, userRepository, tokenService)
    }.start(wait = true)
}

fun Application.module(
    taskRepository: TaskRepository,
    userRepository: UserRepository,
    tokenService: JwtTokenService,
    passwordHasher: PasswordHasher = BcryptPasswordHasher()
) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }

    install(CallLogging) {
        level = Level.INFO
    }

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(tokenService.verifier())
            validate { credential ->
                credential.payload.subject
                    ?.takeIf { it.isNotBlank() }
                    ?.let { io.ktor.server.auth.jwt.JWTPrincipal(credential.payload) }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))
            }
        }
    }

    install(StatusPages) {
        exception<ContentTransformationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body"))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request"))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled error", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("Something went wrong")
            )
        }
    }

    routing {
        get("/health") {
            call.respond(HealthResponse("ok"))
        }
        authRoutes(userRepository, passwordHasher, tokenService)
        authenticate("auth-jwt") {
            taskRoutes(taskRepository)
        }
    }
}
