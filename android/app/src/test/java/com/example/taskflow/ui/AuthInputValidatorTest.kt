package com.example.taskflow.ui

import com.example.taskflow.data.AuthMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthInputValidatorTest {
    @Test
    fun `valid registration normalizes identity fields but preserves password`() {
        val result = AuthInputValidator.validate(
            mode = AuthMode.REGISTER,
            rawDisplayName = "  Ada Lovelace  ",
            rawEmail = "  ADA@Example.COM ",
            rawPassword = " correct horse "
        )

        assertTrue(result is AuthInputResult.Valid)
        result as AuthInputResult.Valid
        assertEquals("Ada Lovelace", result.displayName)
        assertEquals("ada@example.com", result.email)
        assertEquals(" correct horse ", result.password)
    }

    @Test
    fun `login does not require a display name`() {
        val result = AuthInputValidator.validate(
            AuthMode.LOGIN,
            rawDisplayName = "",
            rawEmail = "ada@example.com",
            rawPassword = "correct horse"
        )

        assertTrue(result is AuthInputResult.Valid)
    }

    @Test
    fun `registration rejects missing or long display name`() {
        val missing = validate(displayName = "") as AuthInputResult.Invalid
        val long = validate(displayName = "a".repeat(61)) as AuthInputResult.Invalid

        assertEquals(AuthInputError.DISPLAY_NAME_REQUIRED, missing.displayNameError)
        assertEquals(AuthInputError.DISPLAY_NAME_TOO_LONG, long.displayNameError)
    }

    @Test
    fun `invalid email is rejected in both modes`() {
        val registration = validate(email = "not-an-email") as AuthInputResult.Invalid
        val login = AuthInputValidator.validate(
            AuthMode.LOGIN,
            "",
            "not-an-email",
            "correct horse"
        ) as AuthInputResult.Invalid

        assertEquals(AuthInputError.EMAIL_INVALID, registration.emailError)
        assertEquals(AuthInputError.EMAIL_INVALID, login.emailError)
    }

    @Test
    fun `password must be between 8 and 72 utf8 bytes`() {
        val short = validate(password = "short") as AuthInputResult.Invalid
        val long = validate(password = "a".repeat(73)) as AuthInputResult.Invalid
        val tooManyBytes = validate(password = "é".repeat(40)) as AuthInputResult.Invalid

        assertEquals(AuthInputError.PASSWORD_TOO_SHORT, short.passwordError)
        assertEquals(AuthInputError.PASSWORD_TOO_LONG, long.passwordError)
        assertEquals(AuthInputError.PASSWORD_TOO_LONG, tooManyBytes.passwordError)
        assertNull(short.emailError)
    }

    private fun validate(
        displayName: String = "Ada",
        email: String = "ada@example.com",
        password: String = "correct horse"
    ): AuthInputResult = AuthInputValidator.validate(
        AuthMode.REGISTER,
        displayName,
        email,
        password
    )
}
