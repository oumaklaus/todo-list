package com.example.taskflow.ui

import com.example.taskflow.data.Task
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TaskDateFormatter {
    fun formatDueDate(
        dueAt: Long?,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        if (dueAt == null) return ""
        return SimpleDateFormat("MMM d, yyyy", locale).apply {
            this.timeZone = timeZone
        }.format(Date(dueAt))
    }

    fun isOverdue(task: Task, now: Long = System.currentTimeMillis()): Boolean =
        !task.completed && task.dueAt != null && task.dueAt < now
}
