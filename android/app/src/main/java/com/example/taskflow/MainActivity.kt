package com.example.taskflow

import android.app.DatePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taskflow.data.Priority
import com.example.taskflow.data.Task
import com.example.taskflow.databinding.ActivityMainBinding
import com.example.taskflow.databinding.DialogTaskBinding
import com.example.taskflow.ui.InputError
import com.example.taskflow.ui.TaskAdapter
import com.example.taskflow.ui.TaskDateFormatter
import com.example.taskflow.ui.TaskFilter
import com.example.taskflow.ui.TaskInputResult
import com.example.taskflow.ui.TaskInputValidator
import com.example.taskflow.ui.TaskListViewModel
import com.example.taskflow.ui.UiMessage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: TaskListViewModel by viewModels()

    private val adapter = TaskAdapter(
        onCompletedChanged = { task, completed -> viewModel.setCompleted(task, completed) },
        onEdit = ::showTaskDialog,
        onDelete = ::confirmDelete
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerTasks.layoutManager = LinearLayoutManager(this)
        binding.recyclerTasks.adapter = adapter
        binding.inputSearch.doAfterTextChanged {
            viewModel.setQuery(it?.toString().orEmpty())
        }
        binding.filterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chipToday -> TaskFilter.TODAY
                R.id.chipUpcoming -> TaskFilter.UPCOMING
                R.id.chipCompleted -> TaskFilter.COMPLETED
                else -> TaskFilter.ALL
            }
            viewModel.setFilter(filter)
        }
        binding.fabAdd.setOnClickListener { showTaskDialog(null) }
        binding.buttonRetry.setOnClickListener { viewModel.loadTasks() }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            adapter.submitList(state.visibleTasks)

            binding.progressBar.isVisible = state.isLoading && state.tasks.isEmpty()
            binding.recyclerTasks.isVisible = state.visibleTasks.isNotEmpty()
            binding.emptyState.isVisible =
                !state.isLoading && !state.loadError && state.visibleTasks.isEmpty()
            binding.errorState.isVisible =
                !state.isLoading && state.loadError && state.tasks.isEmpty()

            val isFilteredEmpty = state.tasks.isNotEmpty() && state.visibleTasks.isEmpty()
            binding.textEmptyTitle.setText(
                if (isFilteredEmpty) R.string.no_matches_title else R.string.empty_title
            )
            binding.textEmptySubtitle.setText(
                if (isFilteredEmpty) R.string.no_matches_subtitle else R.string.empty_subtitle
            )

            val openCount = state.tasks.count { !it.completed }
            binding.toolbar.subtitle = resources.getQuantityString(
                R.plurals.open_task_count,
                openCount,
                openCount
            )
        }

        viewModel.message.observe(this) { message ->
            if (message == null) return@observe
            val text = when (message) {
                UiMessage.REFRESH_FAILED -> R.string.error_refresh
                UiMessage.SAVE_FAILED -> R.string.error_save
            }
            Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
            viewModel.messageShown()
        }
    }

    private fun showTaskDialog(task: Task?) {
        val dialogBinding = DialogTaskBinding.inflate(layoutInflater)
        val priorities = Priority.entries
        val priorityLabels = priorities.map { priorityLabel(it) }
        dialogBinding.inputPriority.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, priorityLabels)
        )

        var selectedDueAt = task?.dueAt
        dialogBinding.inputTitle.setText(task?.title.orEmpty())
        dialogBinding.inputNotes.setText(task?.notes.orEmpty())
        dialogBinding.inputCategory.setText(task?.category.orEmpty())
        dialogBinding.inputPriority.setText(
            priorityLabel(task?.priority ?: Priority.MEDIUM),
            false
        )
        updateDueDateText(dialogBinding, selectedDueAt)

        dialogBinding.inputDueDate.setOnClickListener {
            showDatePicker(selectedDueAt) { dueAt ->
                selectedDueAt = dueAt
                updateDueDateText(dialogBinding, selectedDueAt)
            }
        }
        dialogBinding.buttonClearDate.setOnClickListener {
            selectedDueAt = null
            updateDueDateText(dialogBinding, null)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (task == null) R.string.new_task else R.string.edit_task)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                clearErrors(dialogBinding)
                val selectedPriority = priorities.getOrElse(
                    priorityLabels.indexOf(dialogBinding.inputPriority.text.toString())
                ) { Priority.MEDIUM }
                when (val result = TaskInputValidator.validate(
                    rawTitle = dialogBinding.inputTitle.text?.toString().orEmpty(),
                    rawNotes = dialogBinding.inputNotes.text?.toString().orEmpty(),
                    dueAt = selectedDueAt,
                    priority = selectedPriority,
                    rawCategory = dialogBinding.inputCategory.text?.toString().orEmpty()
                )) {
                    is TaskInputResult.Valid -> {
                        if (task == null) {
                            viewModel.addTask(result.draft)
                        } else {
                            viewModel.updateTask(
                                task.copy(
                                    title = result.draft.title,
                                    notes = result.draft.notes,
                                    dueAt = result.draft.dueAt,
                                    priority = result.draft.priority,
                                    category = result.draft.category
                                )
                            )
                        }
                        dialog.dismiss()
                    }
                    is TaskInputResult.Invalid -> showErrors(dialogBinding, result)
                }
            }
        }

        dialog.show()
    }

    private fun showDatePicker(currentDueAt: Long?, onDateSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance().apply {
            if (currentDueAt != null) timeInMillis = currentDueAt
        }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val selected = Calendar.getInstance().apply {
                    set(year, month, day, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                onDateSelected(selected.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDueDateText(binding: DialogTaskBinding, dueAt: Long?) {
        binding.inputDueDate.setText(TaskDateFormatter.formatDueDate(dueAt))
        binding.buttonClearDate.isEnabled = dueAt != null
    }

    private fun clearErrors(binding: DialogTaskBinding) {
        binding.layoutTitle.error = null
        binding.layoutNotes.error = null
        binding.layoutCategory.error = null
        binding.layoutDueDate.error = null
    }

    private fun showErrors(binding: DialogTaskBinding, result: TaskInputResult.Invalid) {
        binding.layoutTitle.error = result.titleError?.let { getString(it.messageResource()) }
        binding.layoutNotes.error = result.notesError?.let { getString(it.messageResource()) }
        binding.layoutCategory.error = result.categoryError?.let { getString(it.messageResource()) }
        binding.layoutDueDate.error = result.dueDateError?.let { getString(it.messageResource()) }
    }

    private fun confirmDelete(task: Task) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_task)
            .setMessage(getString(R.string.delete_task_message, task.title))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteTask(task) }
            .show()
    }

    private fun priorityLabel(priority: Priority): String = getString(
        when (priority) {
            Priority.LOW -> R.string.priority_low
            Priority.MEDIUM -> R.string.priority_medium
            Priority.HIGH -> R.string.priority_high
        }
    )

    private fun InputError.messageResource(): Int = when (this) {
        InputError.TITLE_EMPTY -> R.string.error_title_required
        InputError.TITLE_TOO_LONG -> R.string.error_title_too_long
        InputError.NOTES_TOO_LONG -> R.string.error_notes_too_long
        InputError.CATEGORY_TOO_LONG -> R.string.error_category_too_long
        InputError.DUE_DATE_INVALID -> R.string.error_due_date_invalid
    }
}
