package com.example.taskflow.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.taskflow.R
import com.example.taskflow.data.Priority
import com.example.taskflow.data.Task
import com.example.taskflow.databinding.ItemTaskBinding

class TaskAdapter(
    private val onCompletedChanged: (Task, Boolean) -> Unit,
    private val onEdit: (Task) -> Unit,
    private val onDelete: (Task) -> Unit
) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(TaskDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(
        private val binding: ItemTaskBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task) = with(binding) {
            checkboxCompleted.setOnCheckedChangeListener(null)
            checkboxCompleted.isChecked = task.completed
            checkboxCompleted.contentDescription = root.context.getString(
                if (task.completed) R.string.mark_incomplete else R.string.mark_complete,
                task.title
            )
            checkboxCompleted.setOnCheckedChangeListener { _, checked ->
                onCompletedChanged(task, checked)
            }

            textTitle.text = task.title
            textTitle.paintFlags = if (task.completed) {
                textTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                textTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            textNotes.text = task.notes
            textNotes.isVisible = task.notes.isNotBlank()

            val (priorityText, priorityBackground, priorityColor) = when (task.priority) {
                Priority.LOW -> Triple(
                    R.string.priority_low,
                    R.drawable.bg_priority_low,
                    R.color.priority_low_text
                )
                Priority.MEDIUM -> Triple(
                    R.string.priority_medium,
                    R.drawable.bg_priority_medium,
                    R.color.priority_medium_text
                )
                Priority.HIGH -> Triple(
                    R.string.priority_high,
                    R.drawable.bg_priority_high,
                    R.color.priority_high_text
                )
            }
            textPriority.setText(priorityText)
            textPriority.setBackgroundResource(priorityBackground)
            textPriority.setTextColor(ContextCompat.getColor(root.context, priorityColor))

            textCategory.text = task.category
            textCategory.isVisible = task.category.isNotBlank()

            textDueDate.isVisible = task.dueAt != null
            if (task.dueAt != null) {
                textDueDate.text = root.context.getString(
                    R.string.due_date_format,
                    TaskDateFormatter.formatDueDate(task.dueAt)
                )
                textDueDate.setTextColor(
                    ContextCompat.getColor(
                        root.context,
                        if (TaskDateFormatter.isOverdue(task)) {
                            R.color.priority_high_text
                        } else {
                            R.color.text_secondary
                        }
                    )
                )
            }

            root.alpha = if (task.completed) 0.64f else 1f
            root.setOnClickListener { onEdit(task) }
            buttonEdit.contentDescription = root.context.getString(
                R.string.edit_task_description,
                task.title
            )
            buttonDelete.contentDescription = root.context.getString(
                R.string.delete_task_description,
                task.title
            )
            buttonEdit.setOnClickListener { onEdit(task) }
            buttonDelete.setOnClickListener { onDelete(task) }
        }
    }

    private object TaskDiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean =
            oldItem == newItem
    }
}
