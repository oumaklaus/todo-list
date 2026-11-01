package com.example.taskflow

import com.example.taskflow.auth.BcryptPasswordHasher
import com.example.taskflow.auth.JwtTokenService
import com.example.taskflow.model.UserRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class AuthPrimitivesTest {
    @Test
    fun `jwt signing secret must contain at least 32 bytes`() {
        assertFailsWith<IllegalArgumentException> {
            JwtTokenService(secret = "too-short")
        }
    }

    @Test
    fun `bcrypt hashes verify the right password without storing plaintext`() {
        val hasher = BcryptPasswordHasher()

        val hash = hasher.hash("correct horse battery staple")

        assertNotEquals("correct horse battery staple", hash)
        assertTrue(hasher.verify("correct horse battery staple", hash))
        assertFalse(hasher.verify("wrong password", hash))
    }

    @Test
    fun `issued token contains the authenticated user claims`() {
        val service = JwtTokenService(
            secret = "test-secret-that-is-long-enough-32",
            validityMillis = 60_000
        )
        val user = UserRecord(
            id = "user-123",
            displayName = "Ada",
            email = "ada@example.com",
            passwordHash = "hash",
            createdAt = 1_000
        )

        val token = service.issue(user)
        val decoded = service.verifier().verify(token)

        assertEquals("user-123", decoded.subject)
        assertEquals("ada@example.com", decoded.getClaim("email").asString())
        assertEquals("taskflow-api", decoded.issuer)
        assertTrue(decoded.audience.contains("taskflow-android"))
        assertTrue(decoded.expiresAt.time > decoded.issuedAt.time)
    }
}
