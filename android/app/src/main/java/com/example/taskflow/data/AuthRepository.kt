package com.example.taskflow.data

import com.google.gson.Gson

interface AuthRepository {
    suspend fun authenticate(
        mode: AuthMode,
        displayName: String,
        email: String,
        password: String
    ): AuthResult
}

class RemoteAuthRepository(
    private val api: ApiService = ApiClient.apiService,
    private val gson: Gson = Gson()
) : AuthRepository {
    override suspend fun authenticate(
        mode: AuthMode,
        displayName: String,
        email: String,
        password: String
    ): AuthResult = try {
        val response = when (mode) {
            AuthMode.LOGIN -> api.login(LoginRequest(email, password))
            AuthMode.REGISTER -> api.register(RegisterRequest(displayName, email, password))
        }
        val body = response.body()
        if (response.isSuccessful && body != null) {
            AuthResult.Success(body)
        } else {
            val message = response.errorBody()?.string()
                ?.let { raw -> runCatching { gson.fromJson(raw, ApiErrorResponse::class.java).error }.getOrNull() }
                ?.takeIf { it.isNotBlank() }
                ?: "Could not sign in. Try again."
            AuthResult.Failure(message)
        }
    } catch (_: Exception) {
        AuthResult.Failure("Could not connect. Check your connection and try again.")
    }
}
