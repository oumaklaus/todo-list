package com.example.taskflow

import com.example.taskflow.model.AuthResponse
import com.example.taskflow.model.ErrorResponse
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class AuthRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val users = InMemoryUserRepository()
    private val tasks = InMemoryTaskRepository()

    @BeforeTest
    fun setUp() {
        users.clear()
        tasks.clear()
    }

    @Test
    fun `register creates a normalized account and returns a usable token`() = testApplication {
        application { module(tasks, users, testJwt, testHasher) }

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":" Ada Lovelace ","email":" ADA@Example.COM ","password":"correct horse"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val auth = json.decodeFromString<AuthResponse>(response.bodyAsText())
        assertEquals("Ada Lovelace", auth.user.displayName)
        assertEquals("ada@example.com", auth.user.email)
        val stored = runBlocking { users.findByEmail("ada@example.com") }
        assertNotNull(stored)
        assertNotEquals("correct horse", stored.passwordHash)
        assertFalse(response.bodyAsText().contains("password", ignoreCase = true))
        val tasksResponse = client.get("/tasks") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }
        assertEquals(HttpStatusCode.OK, tasksResponse.status)
    }

    @Test
    fun `duplicate email registration returns conflict`() = testApplication {
        application { module(tasks, users, testJwt, testHasher) }
        runBlocking { users.create("Ada", "ada@example.com", testHasher.hash("correct horse")) }

        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":"Other","email":"ADA@example.com","password":"correct horse"}""")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `login accepts a normalized email and returns a token`() = testApplication {
        application { module(tasks, users, testJwt, testHasher) }
        runBlocking { users.create("Ada", "ada@example.com", testHasher.hash("correct horse")) }

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":" ADA@EXAMPLE.COM ","password":"correct horse"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val auth = json.decodeFromString<AuthResponse>(response.bodyAsText())
        assertEquals("ada@example.com", auth.user.email)
        assertNotNull(testJwt.verifier().verify(auth.token))
    }

    @Test
    fun `wrong password and unknown email return the same response`() = testApplication {
        application { module(tasks, users, testJwt, testHasher) }
        runBlocking { users.create("Ada", "ada@example.com", testHasher.hash("correct horse")) }

        suspend fun login(email: String, password: String) = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password"}""")
        }
        val wrongPassword = login("ada@example.com", "wrong password")
        val unknownEmail = login("missing@example.com", "wrong password")

        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
        assertEquals(HttpStatusCode.Unauthorized, unknownEmail.status)
        assertEquals(wrongPassword.bodyAsText(), unknownEmail.bodyAsText())
        assertEquals(
            "Invalid email or password",
            json.decodeFromString<ErrorResponse>(wrongPassword.bodyAsText()).error
        )
    }

    @Test
    fun `registration validates display name email and password`() = testApplication {
        application { module(tasks, users, testJwt, testHasher) }

        val invalidBodies = listOf(
            """{"displayName":"","email":"ada@example.com","password":"correct horse"}""",
            """{"displayName":"Ada","email":"not-an-email","password":"correct horse"}""",
            """{"displayName":"Ada","email":"ada@example.com","password":"short"}"""
        )

        invalidBodies.forEach { body ->
            val response = client.post("/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Test
    fun `task endpoints reject missing and invalid bearer tokens`() = testApplication {
        application { module(tasks, users, testJwt, testHasher) }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/tasks").status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("/tasks") {
                header(HttpHeaders.Authorization, "Bearer invalid-token")
            }.status
        )
    }

    @Test
    fun `task endpoints reject expired bearer tokens`() = testApplication {
        val expiredTokens = com.example.taskflow.auth.JwtTokenService(
            secret = "test-secret-that-is-long-enough",
            validityMillis = -1_000
        )
        application { module(tasks, users, expiredTokens, testHasher) }
        val expired = expiredTokens.issue(primaryUser)

        val response = client.get("/tasks") {
            header(HttpHeaders.Authorization, "Bearer $expired")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
