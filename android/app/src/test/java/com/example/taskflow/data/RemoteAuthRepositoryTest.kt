package com.example.taskflow.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RemoteAuthRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteAuthRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
        repository = RemoteAuthRepository(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login posts credentials and returns authenticated user`() = runBlocking {
        server.enqueue(authResponse(200))

        val result = repository.authenticate(
            AuthMode.LOGIN,
            displayName = "ignored",
            email = "ada@example.com",
            password = "correct horse"
        )

        assertTrue(result is AuthResult.Success)
        result as AuthResult.Success
        assertEquals("token-123", result.auth.token)
        assertEquals("Ada", result.auth.user.displayName)
        val request = server.takeRequest()
        assertEquals("/auth/login", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"email\":\"ada@example.com\""))
        assertTrue(body.contains("\"password\":\"correct horse\""))
        assertTrue(!body.contains("displayName"))
    }

    @Test
    fun `registration posts display name with credentials`() = runBlocking {
        server.enqueue(authResponse(201))

        val result = repository.authenticate(
            AuthMode.REGISTER,
            displayName = "Ada",
            email = "ada@example.com",
            password = "correct horse"
        )

        assertTrue(result is AuthResult.Success)
        val request = server.takeRequest()
        assertEquals("/auth/register", request.path)
        assertTrue(request.body.readUtf8().contains("\"displayName\":\"Ada\""))
    }

    @Test
    fun `api error message is returned to the caller`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"An account with that email already exists"}""")
        )

        val result = repository.authenticate(
            AuthMode.REGISTER,
            "Ada",
            "ada@example.com",
            "correct horse"
        )

        assertEquals(
            "An account with that email already exists",
            (result as AuthResult.Failure).message
        )
    }

    private fun authResponse(code: Int): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """{"token":"token-123","user":{"id":"user-1","displayName":"Ada","email":"ada@example.com"}}"""
        )
}
