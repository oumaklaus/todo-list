package com.example.taskflow.data

import okhttp3.Interceptor
import okhttp3.Response

class BearerAuthInterceptor(
    private val sessionStore: SessionStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val isAuthRequest = original.url.encodedPath.startsWith("/auth/")
        val token = sessionStore.token
        val request = if (!isAuthRequest && !token.isNullOrBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }

        val response = chain.proceed(request)
        if (!isAuthRequest && response.code == 401) {
            sessionStore.clear()
        }
        return response
    }
}
