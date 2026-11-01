package com.example.taskflow.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class BearerAuthInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var store: FakeSessionStore
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = FakeSessionStore("token-123")
        client = OkHttpClient.Builder()
            .addInterceptor(BearerAuthInterceptor(store))
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `protected request receives bearer token`() {
        server.enqueue(MockResponse().setResponseCode(200))

        client.newCall(Request.Builder().url(server.url("/tasks")).build()).execute().close()

        assertEquals("Bearer token-123", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `auth request does not send an existing bearer token`() {
        server.enqueue(MockResponse().setResponseCode(401))

        client.newCall(Request.Builder().url(server.url("/auth/login")).build()).execute().close()

        assertNull(server.takeRequest().getHeader("Authorization"))
        assertEquals("token-123", store.token)
    }

    @Test
    fun `protected unauthorized response clears session`() {
        server.enqueue(MockResponse().setResponseCode(401))

        client.newCall(Request.Builder().url(server.url("/tasks")).build()).execute().close()

        assertNull(store.token)
        assertEquals(1, store.clearCount)
    }
}

private class FakeSessionStore(initialToken: String?) : SessionStore {
    override var token: String? = initialToken
    override var user: AuthUser? = null
    var clearCount = 0

    override fun save(auth: AuthResponse) {
        token = auth.token
        user = auth.user
    }

    override fun clear() {
        token = null
        user = null
        clearCount++
    }
}
