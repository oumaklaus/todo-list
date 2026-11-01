package com.example.taskflow.ui

import com.example.taskflow.data.AuthMode
import java.util.Locale

enum class AuthInputError {
    DISPLAY_NAME_REQUIRED,
    DISPLAY_NAME_TOO_LONG,
    EMAIL_INVALID,
    PASSWORD_TOO_SHORT,
    PASSWORD_TOO_LONG
}

sealed interface AuthInputResult {
    data class Valid(
        val displayName: String,
        val email: String,
        val password: String
    ) : AuthInputResult

    data class Invalid(
        val displayNameError: AuthInputError? = null,
        val emailError: AuthInputError? = null,
        val passwordError: AuthInputError? = null
    ) : AuthInputResult
}

object AuthInputValidator {
    private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun validate(
        mode: AuthMode,
        rawDisplayName: String,
        rawEmail: String,
        rawPassword: String
    ): AuthInputResult {
        val displayName = rawDisplayName.trim()
        val email = rawEmail.trim().lowercase(Locale.ROOT)
        val displayNameError = when {
            mode == AuthMode.LOGIN -> null
            displayName.isBlank() -> AuthInputError.DISPLAY_NAME_REQUIRED
            displayName.length > 60 -> AuthInputError.DISPLAY_NAME_TOO_LONG
            else -> null
        }
        val emailError = if (email.length > 254 || !emailPattern.matches(email)) {
            AuthInputError.EMAIL_INVALID
        } else {
            null
        }
        val passwordError = when {
            rawPassword.length < 8 -> AuthInputError.PASSWORD_TOO_SHORT
            rawPassword.length > 72 || rawPassword.toByteArray(Charsets.UTF_8).size > 72 ->
                AuthInputError.PASSWORD_TOO_LONG
            else -> null
        }

        return if (displayNameError == null && emailError == null && passwordError == null) {
            AuthInputResult.Valid(displayName, email, rawPassword)
        } else {
            AuthInputResult.Invalid(displayNameError, emailError, passwordError)
        }
    }
}
