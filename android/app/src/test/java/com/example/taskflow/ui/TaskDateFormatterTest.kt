package com.example.taskflow.ui

import com.example.taskflow.data.Priority
import com.example.taskflow.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

class TaskDateFormatterTest {

    @Test
    fun `null due date formats as empty text`() {
        assertEquals("", TaskDateFormatter.formatDueDate(null, Locale.US, UTC))
    }

    @Test
    fun `due date uses a readable medium date`() {
        val dueAt = Instant.parse("2027-01-02T00:00:00Z").toEpochMilli()

        assertEquals("Jan 2, 2027", TaskDateFormatter.formatDueDate(dueAt, Locale.US, UTC))
    }

    @Test
    fun `only active tasks past their due date are overdue`() {
        val now = 2000L

        assertTrue(TaskDateFormatter.isOverdue(task(dueAt = 1000), now))
        assertFalse(TaskDateFormatter.isOverdue(task(dueAt = 3000), now))
        assertFalse(TaskDateFormatter.isOverdue(task(dueAt = null), now))
        assertFalse(TaskDateFormatter.isOverdue(task(dueAt = 1000, completed = true), now))
    }

    private fun task(dueAt: Long?, completed: Boolean = false): Task = Task(
        id = "1",
        title = "Task",
        dueAt = dueAt,
        priority = Priority.MEDIUM,
        completed = completed,
        createdAt = 1,
        updatedAt = 1
    )

    private companion object {
        val UTC: TimeZone = TimeZone.getTimeZone("UTC")
    }
}
