package com.example.taskflow

import android.app.Application
import com.example.taskflow.data.ApiClient
import com.example.taskflow.data.AuthRepository
import com.example.taskflow.data.RemoteAuthRepository
import com.example.taskflow.data.RemoteTaskRepository
import com.example.taskflow.data.SessionStore
import com.example.taskflow.data.SharedPreferencesSessionStore
import com.example.taskflow.data.TaskRepository

class TaskFlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val sessionStore = SharedPreferencesSessionStore(this)
        ApiClient.initialize(sessionStore)
        AppContainer.sessionStore = sessionStore
        AppContainer.authRepository = RemoteAuthRepository()
        AppContainer.taskRepository = RemoteTaskRepository()
    }
}

object AppContainer {
    lateinit var sessionStore: SessionStore
    lateinit var authRepository: AuthRepository
    lateinit var taskRepository: TaskRepository
}
