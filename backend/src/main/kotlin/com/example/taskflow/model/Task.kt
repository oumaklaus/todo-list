package com.example.taskflow.model

import kotlinx.serialization.Serializable

@Serializable
enum class Priority {
    LOW,
    MEDIUM,
    HIGH
}

@Serializable
data class Task(
    val id: String,
    val title: String,
    val notes: String = "",
    val dueAt: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val category: String = "",
    val completed: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class TaskInput(
    val title: String,
    val notes: String = "",
    val dueAt: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val category: String = "",
    val completed: Boolean = false
)

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class HealthResponse(val status: String)
