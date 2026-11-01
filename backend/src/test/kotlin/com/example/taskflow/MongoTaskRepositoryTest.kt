package com.example.taskflow

import com.example.taskflow.model.Priority
import com.example.taskflow.model.TaskInput
import com.example.taskflow.repository.MongoTaskRepository
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import de.bwaldvogel.mongo.MongoServer
import de.bwaldvogel.mongo.backend.memory.MemoryBackend
import kotlinx.coroutines.runBlocking
import org.bson.Document
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MongoTaskRepositoryTest {
    private lateinit var server: MongoServer
    private lateinit var client: MongoClient
    private lateinit var repository: MongoTaskRepository

    @BeforeTest
    fun setUp() {
        server = MongoServer(MemoryBackend())
        val address = server.bind()
        client = MongoClients.create("mongodb://${address.hostString}:${address.port}")
        val collection = client.getDatabase("taskflow_test").getCollection("tasks", Document::class.java)
        repository = MongoTaskRepository(collection)
    }

    @AfterTest
    fun tearDown() {
        client.close()
        server.shutdownNow()
    }

    @Test
    fun `create and find all preserve task fields`() = runBlocking {
        val created = repository.create(
            "user-1",
            TaskInput(
                title = "Submit report",
                notes = "Add references",
                dueAt = 1800000000000,
                priority = Priority.HIGH,
                category = "School"
            )
        )

        val stored = repository.findAll("user-1").single()

        assertEquals(created, stored)
        assertEquals("Submit report", stored.title)
        assertEquals(1800000000000, stored.dueAt)
        assertFalse(stored.completed)
    }

    @Test
    fun `find all orders active dated tasks before undated and completed tasks`() = runBlocking {
        repository.create("user-1", TaskInput(title = "Undated"))
        repository.create("user-1", TaskInput(title = "Later", dueAt = 3000))
        repository.create("user-1", TaskInput(title = "Soon", dueAt = 2000))
        val done = repository.create("user-1", TaskInput(title = "Done", dueAt = 1000))
        repository.update("user-1", done.id, TaskInput(title = "Done", dueAt = 1000, completed = true))

        val titles = repository.findAll("user-1").map { it.title }

        assertEquals(listOf("Soon", "Later", "Undated", "Done"), titles)
    }

    @Test
    fun `update changes editable fields and preserves identity timestamps`() = runBlocking {
        val created = repository.create("user-1", TaskInput(title = "Draft"))

        val updated = repository.update(
            "user-1",
            created.id,
            TaskInput(
                title = "Final",
                notes = "Ready",
                dueAt = 1800000000000,
                priority = Priority.HIGH,
                category = "School",
                completed = true
            )
        )

        assertNotNull(updated)
        assertEquals(created.id, updated.id)
        assertEquals(created.createdAt, updated.createdAt)
        assertTrue(updated.updatedAt >= created.updatedAt)
        assertEquals("Final", updated.title)
        assertTrue(updated.completed)
    }

    @Test
    fun `invalid and missing ids return absent results`() = runBlocking {
        assertNull(repository.update("user-1", "not-an-object-id", TaskInput(title = "Task")))
        assertNull(
            repository.update(
                "user-1",
                "64b7f9a2c2a4e4a1b2c3d4e5",
                TaskInput(title = "Task")
            )
        )
        assertFalse(repository.delete("user-1", "not-an-object-id"))
        assertFalse(repository.delete("user-1", "64b7f9a2c2a4e4a1b2c3d4e5"))
    }

    @Test
    fun `delete removes the task`() = runBlocking {
        val created = repository.create("user-1", TaskInput(title = "Remove me"))

        assertTrue(repository.delete("user-1", created.id))
        assertEquals(emptyList(), repository.findAll("user-1"))
    }

    @Test
    fun `task ids cannot cross user boundaries`() = runBlocking {
        val otherUsersTask = repository.create("user-2", TaskInput(title = "Private"))

        assertEquals(emptyList(), repository.findAll("user-1"))
        assertNull(repository.update("user-1", otherUsersTask.id, TaskInput(title = "Stolen")))
        assertFalse(repository.delete("user-1", otherUsersTask.id))
        assertEquals("Private", repository.findAll("user-2").single().title)
    }
}
