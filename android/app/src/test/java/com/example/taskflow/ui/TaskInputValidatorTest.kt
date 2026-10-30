package com.example.taskflow.ui

import com.example.taskflow.data.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskInputValidatorTest {

    @Test
    fun `valid input is trimmed and returned`() {
        val result = TaskInputValidator.validate(
            rawTitle = "  Submit report  ",
            rawNotes = "  Add references  ",
            dueAt = 1800000000000,
            priority = Priority.HIGH,
            rawCategory = "  School  "
        )

        assertTrue(result is TaskInputResult.Valid)
        val valid = result as TaskInputResult.Valid
        assertEquals("Submit report", valid.draft.title)
        assertEquals("Add references", valid.draft.notes)
        assertEquals(1800000000000, valid.draft.dueAt)
        assertEquals(Priority.HIGH, valid.draft.priority)
        assertEquals("School", valid.draft.category)
    }

    @Test
    fun `optional fields may be empty`() {
        val result = TaskInputValidator.validate(
            rawTitle = "Task",
            rawNotes = "",
            dueAt = null,
            priority = Priority.MEDIUM,
            rawCategory = ""
        )

        assertTrue(result is TaskInputResult.Valid)
        val valid = result as TaskInputResult.Valid
        assertEquals("", valid.draft.notes)
        assertNull(valid.draft.dueAt)
        assertEquals("", valid.draft.category)
    }

    @Test
    fun `blank title is rejected`() {
        val result = validate(title = "   ")

        assertEquals(InputError.TITLE_EMPTY, (result as TaskInputResult.Invalid).titleError)
    }

    @Test
    fun `title over 120 characters is rejected`() {
        val result = validate(title = "a".repeat(121))

        assertEquals(InputError.TITLE_TOO_LONG, (result as TaskInputResult.Invalid).titleError)
    }

    @Test
    fun `notes over 1000 characters are rejected`() {
        val result = validate(notes = "a".repeat(1001))

        assertEquals(InputError.NOTES_TOO_LONG, (result as TaskInputResult.Invalid).notesError)
    }

    @Test
    fun `category over 40 characters is rejected`() {
        val result = validate(category = "a".repeat(41))

        assertEquals(InputError.CATEGORY_TOO_LONG, (result as TaskInputResult.Invalid).categoryError)
    }

    @Test
    fun `non positive due date is rejected`() {
        val result = validate(dueAt = 0)

        assertEquals(InputError.DUE_DATE_INVALID, (result as TaskInputResult.Invalid).dueDateError)
    }

    private fun validate(
        title: String = "Task",
        notes: String = "",
        dueAt: Long? = null,
        category: String = ""
    ): TaskInputResult = TaskInputValidator.validate(
        rawTitle = title,
        rawNotes = notes,
        dueAt = dueAt,
        priority = Priority.MEDIUM,
        rawCategory = category
    )
}
