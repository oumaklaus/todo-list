package com.example.taskflow.repository

import com.example.taskflow.model.Priority
import com.example.taskflow.model.Task
import com.example.taskflow.model.TaskInput
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.types.ObjectId

class MongoTaskRepository(
    private val collection: MongoCollection<Document>
) : TaskRepository {

    override suspend fun findAll(ownerId: String): List<Task> = withContext(Dispatchers.IO) {
        collection.find(Filters.eq("ownerId", ownerId))
            .map { it.toTask() }
            .toList()
            .sortedWith(
                compareBy<Task> { it.completed }
                    .thenBy { it.dueAt ?: Long.MAX_VALUE }
                    .thenByDescending { it.createdAt }
            )
    }

    override suspend fun create(ownerId: String, input: TaskInput): Task = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val document = Document("_id", ObjectId())
            .append("ownerId", ownerId)
            .append("title", input.title)
            .append("notes", input.notes)
            .append("dueAt", input.dueAt)
            .append("priority", input.priority.name)
            .append("category", input.category)
            .append("completed", input.completed)
            .append("createdAt", now)
            .append("updatedAt", now)

        collection.insertOne(document)
        document.toTask()
    }

    override suspend fun update(ownerId: String, id: String, input: TaskInput): Task? =
        withContext(Dispatchers.IO) {
            val objectId = id.toObjectId() ?: return@withContext null
            val values = Document("title", input.title)
                .append("notes", input.notes)
                .append("dueAt", input.dueAt)
                .append("priority", input.priority.name)
                .append("category", input.category)
                .append("completed", input.completed)
                .append("updatedAt", System.currentTimeMillis())

            val ownerTask = Filters.and(
                Filters.eq("_id", objectId),
                Filters.eq("ownerId", ownerId)
            )
            val result = collection.updateOne(
                ownerTask,
                Document("\$set", values)
            )
            if (result.matchedCount == 0L) return@withContext null

            collection.find(ownerTask).first()?.toTask()
        }

    override suspend fun delete(ownerId: String, id: String): Boolean = withContext(Dispatchers.IO) {
        val objectId = id.toObjectId() ?: return@withContext false
        collection.deleteOne(
            Filters.and(
                Filters.eq("_id", objectId),
                Filters.eq("ownerId", ownerId)
            )
        ).deletedCount > 0
    }
}

private fun String.toObjectId(): ObjectId? =
    if (ObjectId.isValid(this)) ObjectId(this) else null

private fun Document.toTask(): Task = Task(
    id = getObjectId("_id").toHexString(),
    title = getString("title") ?: "",
    notes = getString("notes") ?: "",
    dueAt = (get("dueAt") as? Number)?.toLong(),
    priority = runCatching {
        Priority.valueOf(getString("priority") ?: Priority.MEDIUM.name)
    }.getOrDefault(Priority.MEDIUM),
    category = getString("category") ?: "",
    completed = getBoolean("completed") ?: false,
    createdAt = (get("createdAt") as? Number)?.toLong() ?: 0L,
    updatedAt = (get("updatedAt") as? Number)?.toLong() ?: 0L
)
