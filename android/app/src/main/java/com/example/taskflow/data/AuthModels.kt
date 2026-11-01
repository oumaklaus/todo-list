package com.example.taskflow.data

enum class AuthMode {
    LOGIN,
    REGISTER
}

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val displayName: String,
    val email: String,
    val password: String
)

data class AuthUser(
    val id: String,
    val displayName: String,
    val email: String
)

data class AuthResponse(
    val token: String,
    val user: AuthUser
)

data class ApiErrorResponse(val error: String)

sealed interface AuthResult {
    data class Success(val auth: AuthResponse) : AuthResult
    data class Failure(val message: String) : AuthResult
}
