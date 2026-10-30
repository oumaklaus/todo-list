package com.example.taskflow.data

import java.io.IOException

interface TaskRepository {
    suspend fun getTasks(): Result<List<Task>>
    suspend fun addTask(draft: TaskDraft): Result<Task>
    suspend fun updateTask(task: Task): Result<Task>
    suspend fun deleteTask(id: String): Result<Unit>
}

class RemoteTaskRepository(
    private val api: ApiService = ApiClient.apiService
) : TaskRepository {

    override suspend fun getTasks(): Result<List<Task>> = runCatching {
        api.getTasks()
    }

    override suspend fun addTask(draft: TaskDraft): Result<Task> = runCatching {
        api.addTask(
            TaskRequest(
                title = draft.title,
                notes = draft.notes,
                dueAt = draft.dueAt,
                priority = draft.priority,
                category = draft.category,
                completed = false
            )
        )
    }

    override suspend fun updateTask(task: Task): Result<Task> = runCatching {
        api.updateTask(
            id = task.id,
            request = TaskRequest(
                title = task.title,
                notes = task.notes,
                dueAt = task.dueAt,
                priority = task.priority,
                category = task.category,
                completed = task.completed
            )
        )
    }

    override suspend fun deleteTask(id: String): Result<Unit> = runCatching {
        val response = api.deleteTask(id)
        if (!response.isSuccessful) {
            throw IOException("Delete failed with HTTP ${response.code()}")
        }
    }
}
