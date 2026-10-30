package com.example.taskflow.ui

import com.example.taskflow.data.Priority
import com.example.taskflow.data.TaskDraft

enum class InputError {
    TITLE_EMPTY,
    TITLE_TOO_LONG,
    NOTES_TOO_LONG,
    CATEGORY_TOO_LONG,
    DUE_DATE_INVALID
}

sealed interface TaskInputResult {
    data class Valid(val draft: TaskDraft) : TaskInputResult

    data class Invalid(
        val titleError: InputError? = null,
        val notesError: InputError? = null,
        val categoryError: InputError? = null,
        val dueDateError: InputError? = null
    ) : TaskInputResult
}

object TaskInputValidator {
    fun validate(
        rawTitle: String,
        rawNotes: String,
        dueAt: Long?,
        priority: Priority,
        rawCategory: String
    ): TaskInputResult {
        val title = rawTitle.trim()
        val notes = rawNotes.trim()
        val category = rawCategory.trim()

        val titleError = when {
            title.isEmpty() -> InputError.TITLE_EMPTY
            title.length > 120 -> InputError.TITLE_TOO_LONG
            else -> null
        }
        val notesError = if (notes.length > 1000) InputError.NOTES_TOO_LONG else null
        val categoryError = if (category.length > 40) InputError.CATEGORY_TOO_LONG else null
        val dueDateError = if (dueAt != null && dueAt <= 0) {
            InputError.DUE_DATE_INVALID
        } else {
            null
        }

        if (
            titleError != null || notesError != null ||
            categoryError != null || dueDateError != null
        ) {
            return TaskInputResult.Invalid(
                titleError = titleError,
                notesError = notesError,
                categoryError = categoryError,
                dueDateError = dueDateError
            )
        }

        return TaskInputResult.Valid(
            TaskDraft(
                title = title,
                notes = notes,
                dueAt = dueAt,
                priority = priority,
                category = category
            )
        )
    }
}
