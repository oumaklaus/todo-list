package com.example.taskflow

import com.example.taskflow.model.Priority
import com.example.taskflow.model.Task
import com.example.taskflow.model.TaskInput
import com.example.taskflow.repository.TaskRepository
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val repository = InMemoryTaskRepository()
    private val users = InMemoryUserRepository()
    private val token = testJwt.issue(primaryUser)

    @BeforeTest
    fun setUp() {
        repository.clear()
        users.clear()
    }

    @Test
    fun `health endpoint reports ok`() = testApplication {
        application { module(repository, users, testJwt, testHasher) }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"ok"}""", response.bodyAsText())
    }

    @Test
    fun `get tasks returns empty list initially`() = testApplication {
        application { module(repository, users, testJwt, testHasher) }

        val response = client.get("/tasks") { authenticated() }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(emptyList(), json.decodeFromString<List<Task>>(response.bodyAsText()))
    }

    @Test
    fun `post task creates task with optional details`() = testApplication {
        application { module(repository, users, testJwt, testHasher) }

        val response = client.post("/tasks") {
            authenticated()
            contentType(ContentType.Application.Json)
            setBody(
                """{"title":"Submit report","notes":"Add references","dueAt":1800000000000,"priority":"HIGH","category":"School"}"""
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val task = json.decodeFromString<Task>(response.bodyAsText())
        assertEquals("Submit report", task.title)
        assertEquals("Add references", task.notes)
        assertEquals(1800000000000, task.dueAt)
        assertEquals(Priority.HIGH, task.priority)
        assertEquals("School", task.category)
        assertFalse(task.completed)
        assertTrue(task.id.isNotBlank())
        assertTrue(task.createdAt > 0)
        assertEquals(task.createdAt, task.updatedAt)
    }

    @Test
    fun `post task applies defaults and trims text`() = testApplication {
        application { module(repository, users, testJwt, testHasher) }

        val response = client.post("/tasks") {
            authenticated()
            contentType(ContentType.Application.Json)
            setBody("""{"title":"  Read chapter 4  ","notes":"  pages 40-60  ","category":"  Study  "}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val task = json.decodeFromString<Task>(response.bodyAsText())
        assertEquals("Read chapter 4", task.title)
        assertEquals("pages 40-60", task.notes)
        assertEquals("Study", task.category)
        assertEquals(Priority.MEDIUM, task.priority)
        assertEquals(null, task.dueAt)
        assertFalse(task.completed)
    }

    @Test
    fun `post task rejects blank title`() = testApplication {
        application { module(repository, users, testJwt, testHasher) }

        val response = client.post("/tasks") {
            authenticated()
            contentType(ContentType.Application.Json)
            setBody("""{"title":"   "}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `post task rejects title over 120 characters`() = testApplication {
        application { module(repository, users, testJwt, testHasher) }
        val title = "a".repeat(121)

        val response = client.post("/tasks") {
            authenticated()
            contentType(ContentType.Application.Json)
            setBody("""{"title":"$title"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `post task rejects notes over 1000 characters`() = testApplication {
        application { module(repository, users, testJwt, testHasher) }
        val notes = "a".repeat(1001)

        val response = client.post("/tasks") {
            authenticated()
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Task","notes":"$notes"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `post task rejects category over 40 characters`() = testApplication {
        application { module(repository, users, testJwt, testHasher) }
        val category = "a".repeat(41)

        val response = client.post("/tasks") {
            authenticated()
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Task","category":"$category"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `post task rejects non positive due date`() = testApplication {
        application { module(repository, users, testJwt, testHasher) }

        val response = client.post("/tasks") {
            authenticated()
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Task","dueAt":0}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `put task updates every editable field`() = testApplication {
        application { module(repository, users, testJwt, testHasher) }
        val created = repository.create(primaryUser.id, TaskInput(title = "Draft essay"))

        val response = client.put("/tasks/${created.id}") {
            authenticated()
            contentType(ContentType.Application.Json)
            setBody(
                """{"title":"Submit essay","notes":"Final copy","dueAt":1800000000000,"priority":"HIGH","category":"School","completed":true}"""
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val updated = json.decodeFromString<Task>(response.bodyAsText())
        assertEquals("Submit essay", updated.title)
        assertEquals("Final copy", updated.notes)
        assertEquals(1800000000000, updated.dueAt)
        assertEquals(Priority.HIGH, updated.priority)
        assertEquals("School", updated.category)
        assertTrue(updated.completed)
        assertTrue(updated.updatedAt >= updated.createdAt)
    }

    @Test
    fun `put missing task returns 404`() = testApplication {
        application { module(repository, users, testJwt, testHasher) }

        val response = client.put("/tasks/missing") {
            authenticated()
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Task"}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `delete task removes it`() = testApplication {
        application { module(repository, users, testJwt, testHasher) }
        val created = repository.create(primaryUser.id, TaskInput(title = "Old task"))

        val response = client.delete("/tasks/${created.id}") { authenticated() }

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(emptyList(), repository.findAll(primaryUser.id))
    }

    @Test
    fun `delete missing task returns 404`() = testApplication {
        application { module(repository, users, testJwt, testHasher) }

        val response = client.delete("/tasks/missing") { authenticated() }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `known task id cannot be changed by another user`() = testApplication {
        application { module(repository, users, testJwt, testHasher) }
        val otherUsersTask = repository.create("user-2", TaskInput(title = "Private"))

        val update = client.put("/tasks/${otherUsersTask.id}") {
            authenticated()
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Stolen"}""")
        }
        val delete = client.delete("/tasks/${otherUsersTask.id}") { authenticated() }

        assertEquals(HttpStatusCode.NotFound, update.status)
        assertEquals(HttpStatusCode.NotFound, delete.status)
        assertEquals("Private", repository.findAll("user-2").single().title)
    }

    @Test
    fun `malformed json returns bad request`() = testApplication {
        application { module(repository, users, testJwt, testHasher) }

        val response = client.post("/tasks") {
            authenticated()
            contentType(ContentType.Application.Json)
            setBody("""{"title":"""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authenticated() {
        header(HttpHeaders.Authorization, "Bearer $token")
    }
}
