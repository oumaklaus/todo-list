package com.example.taskflow.data

import com.example.taskflow.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    @Volatile
    private var configuredService: ApiService? = null

    val apiService: ApiService
        get() = checkNotNull(configuredService) { "ApiClient.initialize must be called first" }

    fun initialize(sessionStore: SessionStore) {
        if (configuredService != null) return
        synchronized(this) {
            if (configuredService == null) {
                configuredService = createService(sessionStore)
            }
        }
    }

    private fun createService(sessionStore: SessionStore): ApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor(BearerAuthInterceptor(sessionStore))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
