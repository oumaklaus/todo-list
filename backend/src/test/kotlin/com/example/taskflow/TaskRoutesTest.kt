package com.example.taskflow

import com.example.taskflow.model.Priority
import com.example.taskflow.model.Task
import com.example.taskflow.model.TaskInput
import com.example.taskflow.repository.TaskRepository
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
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

    @BeforeTest
    fun setUp() {
        repository.clear()
    }

    @Test
    fun `health endpoint reports ok`() = testApplication {
        application { module(repository) }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"ok"}""", response.bodyAsText())
    }

    @Test
    fun `get tasks returns empty list initially`() = testApplication {
        application { module(repository) }

        val response = client.get("/tasks")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(emptyList(), json.decodeFromString<List<Task>>(response.bodyAsText()))
    }

    @Test
    fun `post task creates task with optional details`() = testApplication {
        application { module(repository) }

        val response = client.post("/tasks") {
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
        application { module(repository) }

        val response = client.post("/tasks") {
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
        application { module(repository) }

        val response = client.post("/tasks") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"   "}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `post task rejects title over 120 characters`() = testApplication {
        application { module(repository) }
        val title = "a".repeat(121)

        val response = client.post("/tasks") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"$title"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `post task rejects notes over 1000 characters`() = testApplication {
        application { module(repository) }
        val notes = "a".repeat(1001)

        val response = client.post("/tasks") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Task","notes":"$notes"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `post task rejects category over 40 characters`() = testApplication {
        application { module(repository) }
        val category = "a".repeat(41)

        val response = client.post("/tasks") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Task","category":"$category"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `post task rejects non positive due date`() = testApplication {
        application { module(repository) }

        val response = client.post("/tasks") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Task","dueAt":0}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `put task updates every editable field`() = testApplication {
        application { module(repository) }
        val created = repository.create(TaskInput(title = "Draft essay"))

        val response = client.put("/tasks/${created.id}") {
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
        application { module(repository) }

        val response = client.put("/tasks/missing") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Task"}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `delete task removes it`() = testApplication {
        application { module(repository) }
        val created = repository.create(TaskInput(title = "Old task"))

        val response = client.delete("/tasks/${created.id}")

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(emptyList(), repository.findAll())
    }

    @Test
    fun `delete missing task returns 404`() = testApplication {
        application { module(repository) }

        val response = client.delete("/tasks/missing")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `malformed json returns bad request`() = testApplication {
        application { module(repository) }

        val response = client.post("/tasks") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}

private class InMemoryTaskRepository : TaskRepository {
    private val tasks = mutableListOf<Task>()
    private var nextId = 1
    private var clock = 1000L

    fun clear() {
        tasks.clear()
        nextId = 1
        clock = 1000L
    }

    override suspend fun findAll(): List<Task> = tasks.toList()

    override suspend fun create(input: TaskInput): Task {
        val now = clock++
        val task = Task(
            id = "id-${nextId++}",
            title = input.title,
            notes = input.notes,
            dueAt = input.dueAt,
            priority = input.priority,
            category = input.category,
            completed = input.completed,
            createdAt = now,
            updatedAt = now
        )
        tasks.add(task)
        return task
    }

    override suspend fun update(id: String, input: TaskInput): Task? {
        val index = tasks.indexOfFirst { it.id == id }
        if (index == -1) return null
        val updated = tasks[index].copy(
            title = input.title,
            notes = input.notes,
            dueAt = input.dueAt,
            priority = input.priority,
            category = input.category,
            completed = input.completed,
            updatedAt = clock++
        )
        tasks[index] = updated
        return updated
    }

    override suspend fun delete(id: String): Boolean = tasks.removeIf { it.id == id }
}
