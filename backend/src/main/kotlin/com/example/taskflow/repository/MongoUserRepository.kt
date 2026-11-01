package com.example.taskflow.repository

import com.example.taskflow.model.UserRecord
import com.mongodb.MongoWriteException
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.types.ObjectId

class MongoUserRepository(
    private val collection: MongoCollection<Document>
) : UserRepository {
    init {
        collection.createIndex(Indexes.ascending("email"), IndexOptions().unique(true))
    }

    override suspend fun findByEmail(email: String): UserRecord? = withContext(Dispatchers.IO) {
        collection.find(Filters.eq("email", email.normalizedEmail())).first()?.toUserRecord()
    }

    override suspend fun create(
        displayName: String,
        email: String,
        passwordHash: String
    ): UserRecord? = withContext(Dispatchers.IO) {
        val document = Document("_id", ObjectId())
            .append("displayName", displayName.trim())
            .append("email", email.normalizedEmail())
            .append("passwordHash", passwordHash)
            .append("createdAt", System.currentTimeMillis())
        try {
            collection.insertOne(document)
            document.toUserRecord()
        } catch (error: MongoWriteException) {
            if (error.code == 11000) null else throw error
        }
    }
}

private fun String.normalizedEmail(): String = trim().lowercase()

private fun Document.toUserRecord(): UserRecord = UserRecord(
    id = getObjectId("_id").toHexString(),
    displayName = getString("displayName") ?: "",
    email = getString("email") ?: "",
    passwordHash = getString("passwordHash") ?: "",
    createdAt = (get("createdAt") as? Number)?.toLong() ?: 0L
)
