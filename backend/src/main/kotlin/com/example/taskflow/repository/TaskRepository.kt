package com.example.taskflow.repository

import com.example.taskflow.model.Task
import com.example.taskflow.model.TaskInput

interface TaskRepository {
    suspend fun findAll(): List<Task>
    suspend fun create(input: TaskInput): Task
    suspend fun update(id: String, input: TaskInput): Task?
    suspend fun delete(id: String): Boolean
}
