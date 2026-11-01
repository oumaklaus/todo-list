package com.example.taskflow.data

import android.content.Context

interface SessionStore {
    var token: String?
    var user: AuthUser?
    fun save(auth: AuthResponse)
    fun clear()
}

class SharedPreferencesSessionStore(context: Context) : SessionStore {
    private val preferences = context.getSharedPreferences("taskflow_session", Context.MODE_PRIVATE)

    override var token: String?
        get() = preferences.getString(KEY_TOKEN, null)
        set(value) {
            preferences.edit().putString(KEY_TOKEN, value).apply()
        }

    override var user: AuthUser?
        get() {
            val id = preferences.getString(KEY_USER_ID, null) ?: return null
            val displayName = preferences.getString(KEY_DISPLAY_NAME, null) ?: return null
            val email = preferences.getString(KEY_EMAIL, null) ?: return null
            return AuthUser(id, displayName, email)
        }
        set(value) {
            preferences.edit()
                .putString(KEY_USER_ID, value?.id)
                .putString(KEY_DISPLAY_NAME, value?.displayName)
                .putString(KEY_EMAIL, value?.email)
                .apply()
        }

    override fun save(auth: AuthResponse) {
        preferences.edit()
            .putString(KEY_TOKEN, auth.token)
            .putString(KEY_USER_ID, auth.user.id)
            .putString(KEY_DISPLAY_NAME, auth.user.displayName)
            .putString(KEY_EMAIL, auth.user.email)
            .apply()
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_USER_ID = "user_id"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_EMAIL = "email"
    }
}
