package com.example.taskflow.model

import kotlinx.serialization.Serializable

data class UserRecord(
    val id: String,
    val displayName: String,
    val email: String,
    val passwordHash: String,
    val createdAt: Long
)

@Serializable
data class RegisterRequest(
    val displayName: String,
    val email: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class AuthUser(
    val id: String,
    val displayName: String,
    val email: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: AuthUser
)

fun UserRecord.toAuthUser(): AuthUser = AuthUser(
    id = id,
    displayName = displayName,
    email = email
)
