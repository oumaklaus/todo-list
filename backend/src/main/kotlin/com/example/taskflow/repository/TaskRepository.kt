package com.example.taskflow.repository

import com.example.taskflow.model.Task
import com.example.taskflow.model.TaskInput

interface TaskRepository {
    suspend fun findAll(ownerId: String): List<Task>
    suspend fun create(ownerId: String, input: TaskInput): Task
    suspend fun update(ownerId: String, id: String, input: TaskInput): Task?
    suspend fun delete(ownerId: String, id: String): Boolean
}
