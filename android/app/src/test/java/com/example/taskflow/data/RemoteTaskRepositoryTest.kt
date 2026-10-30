package com.example.taskflow.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RemoteTaskRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteTaskRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
        repository = RemoteTaskRepository(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `get tasks parses all fields`() = runBlocking {
        server.enqueue(
            jsonResponse(
                200,
                """[{"id":"1","title":"Report","notes":"Finish it","dueAt":1800000000000,"priority":"HIGH","category":"School","completed":false,"createdAt":100,"updatedAt":101}]"""
            )
        )

        val task = repository.getTasks().getOrThrow().single()

        assertEquals("Report", task.title)
        assertEquals("Finish it", task.notes)
        assertEquals(1800000000000, task.dueAt)
        assertEquals(Priority.HIGH, task.priority)
        assertEquals("School", task.category)
        assertFalse(task.completed)
        assertEquals(101, task.updatedAt)
    }

    @Test
    fun `add task sends the draft and parses created task`() = runBlocking {
        server.enqueue(
            jsonResponse(
                201,
                """{"id":"2","title":"Read","notes":"Chapter 4","dueAt":null,"priority":"MEDIUM","category":"Study","completed":false,"createdAt":200,"updatedAt":200}"""
            )
        )

        val result = repository.addTask(
            TaskDraft("Read", "Chapter 4", null, Priority.MEDIUM, "Study")
        )

        assertTrue(result.isSuccess)
        assertEquals("2", result.getOrThrow().id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/tasks", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"title\":\"Read\""))
        assertTrue(body.contains("\"notes\":\"Chapter 4\""))
        assertTrue(body.contains("\"priority\":\"MEDIUM\""))
        assertTrue(body.contains("\"completed\":false"))
    }

    @Test
    fun `update task sends all editable fields`() = runBlocking {
        server.enqueue(
            jsonResponse(
                200,
                """{"id":"2","title":"Read","notes":"Done","dueAt":1800000000000,"priority":"LOW","category":"Study","completed":true,"createdAt":200,"updatedAt":300}"""
            )
        )
        val task = Task(
            id = "2",
            title = "Read",
            notes = "Done",
            dueAt = 1800000000000,
            priority = Priority.LOW,
            category = "Study",
            completed = true,
            createdAt = 200,
            updatedAt = 300
        )

        val result = repository.updateTask(task)

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/tasks/2", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"dueAt\":1800000000000"))
        assertTrue(body.contains("\"priority\":\"LOW\""))
        assertTrue(body.contains("\"completed\":true"))
    }

    @Test
    fun `delete task succeeds on 204`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = repository.deleteTask("2")

        assertTrue(result.isSuccess)
        assertEquals("/tasks/2", server.takeRequest().path)
    }

    @Test
    fun `delete task fails on error status`() = runBlocking {
        server.enqueue(jsonResponse(404, """{"error":"Task not found"}"""))

        assertTrue(repository.deleteTask("missing").isFailure)
    }

    @Test
    fun `get tasks fails on server error`() = runBlocking {
        server.enqueue(jsonResponse(500, """{"error":"Something went wrong"}"""))

        assertTrue(repository.getTasks().isFailure)
    }

    private fun jsonResponse(code: Int, body: String): MockResponse =
        MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
}
