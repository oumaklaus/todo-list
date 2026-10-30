package com.example.taskflow.data

enum class Priority {
    LOW,
    MEDIUM,
    HIGH
}

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

data class TaskDraft(
    val title: String,
    val notes: String = "",
    val dueAt: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val category: String = ""
)

data class TaskRequest(
    val title: String,
    val notes: String = "",
    val dueAt: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val category: String = "",
    val completed: Boolean = false
)
