package com.example.taskflow

import com.example.taskflow.auth.BcryptPasswordHasher
import com.example.taskflow.auth.JwtTokenService
import com.example.taskflow.model.Task
import com.example.taskflow.model.TaskInput
import com.example.taskflow.model.UserRecord
import com.example.taskflow.repository.TaskRepository
import com.example.taskflow.repository.UserRepository

internal val testJwt = JwtTokenService("test-secret-that-is-long-enough")
internal val testHasher = BcryptPasswordHasher(logRounds = 4)
internal val primaryUser = UserRecord(
    id = "user-1",
    displayName = "Ada",
    email = "ada@example.com",
    passwordHash = "unused",
    createdAt = 1_000
)

internal class InMemoryUserRepository : UserRepository {
    private val users = mutableListOf<UserRecord>()
    private var nextId = 1

    fun clear() {
        users.clear()
        nextId = 1
    }

    override suspend fun findByEmail(email: String): UserRecord? =
        users.firstOrNull { it.email == email.trim().lowercase() }

    override suspend fun create(
        displayName: String,
        email: String,
        passwordHash: String
    ): UserRecord? {
        val normalized = email.trim().lowercase()
        if (users.any { it.email == normalized }) return null
        return UserRecord(
            id = "user-${nextId++}",
            displayName = displayName.trim(),
            email = normalized,
            passwordHash = passwordHash,
            createdAt = 1_000
        ).also(users::add)
    }
}

internal class InMemoryTaskRepository : TaskRepository {
    private data class OwnedTask(val ownerId: String, val task: Task)

    private val tasks = mutableListOf<OwnedTask>()
    private var nextId = 1
    private var clock = 1_000L

    fun clear() {
        tasks.clear()
        nextId = 1
        clock = 1_000L
    }

    override suspend fun findAll(ownerId: String): List<Task> =
        tasks.filter { it.ownerId == ownerId }.map { it.task }

    override suspend fun create(ownerId: String, input: TaskInput): Task {
        val now = clock++
        val task = Task(
            id = "id-${nextId++}",
            title = input.title,
            notes = input.notes,
            dueAt = input.dueAt,
            priority = input.priority,
            category = input.category,
            completed = input.completed,
            createdAt = now,
            updatedAt = now
        )
        tasks.add(OwnedTask(ownerId, task))
        return task
    }

    override suspend fun update(ownerId: String, id: String, input: TaskInput): Task? {
        val index = tasks.indexOfFirst { it.ownerId == ownerId && it.task.id == id }
        if (index == -1) return null
        val updated = tasks[index].task.copy(
            title = input.title,
            notes = input.notes,
            dueAt = input.dueAt,
            priority = input.priority,
            category = input.category,
            completed = input.completed,
            updatedAt = clock++
        )
        tasks[index] = OwnedTask(ownerId, updated)
        return updated
    }

    override suspend fun delete(ownerId: String, id: String): Boolean =
        tasks.removeIf { it.ownerId == ownerId && it.task.id == id }
}
