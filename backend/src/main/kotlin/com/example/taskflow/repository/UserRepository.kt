package com.example.taskflow.repository

import com.example.taskflow.model.UserRecord

interface UserRepository {
    suspend fun findByEmail(email: String): UserRecord?
    suspend fun create(
        displayName: String,
        email: String,
        passwordHash: String
    ): UserRecord?
}
