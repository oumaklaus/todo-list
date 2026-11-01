package com.example.taskflow

import com.example.taskflow.repository.MongoUserRepository
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

class MongoUserRepositoryTest {
    private lateinit var server: MongoServer
    private lateinit var client: MongoClient
    private lateinit var collection: com.mongodb.client.MongoCollection<Document>
    private lateinit var repository: MongoUserRepository

    @BeforeTest
    fun setUp() {
        server = MongoServer(MemoryBackend())
        val address = server.bind()
        client = MongoClients.create("mongodb://${address.hostString}:${address.port}")
        collection = client.getDatabase("taskflow_test").getCollection("users", Document::class.java)
        repository = MongoUserRepository(collection)
    }

    @AfterTest
    fun tearDown() {
        client.close()
        server.shutdownNow()
    }

    @Test
    fun `create normalizes email and stores only the password hash`() = runBlocking {
        val created = repository.create(
            displayName = "Ada Lovelace",
            email = "  Ada@Example.COM ",
            passwordHash = "bcrypt-hash"
        )

        assertNotNull(created)
        assertEquals("ada@example.com", created.email)
        assertEquals(created, repository.findByEmail(" ADA@example.com "))
        val stored = collection.find().first()
        assertNotNull(stored)
        assertEquals("bcrypt-hash", stored.getString("passwordHash"))
        assertFalse(stored.containsKey("password"))
    }

    @Test
    fun `case variants cannot create duplicate email accounts`() = runBlocking {
        assertNotNull(repository.create("Ada", "ada@example.com", "hash-1"))

        val duplicate = repository.create("Other Ada", "ADA@EXAMPLE.COM", "hash-2")

        assertNull(duplicate)
        assertEquals(1L, collection.countDocuments())
    }
}
