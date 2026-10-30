package com.example.taskflow.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.data.RemoteTaskRepository
import com.example.taskflow.data.Task
import com.example.taskflow.data.TaskDraft
import com.example.taskflow.data.TaskRepository
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

enum class TaskFilter {
    ALL,
    TODAY,
    UPCOMING,
    COMPLETED
}

enum class UiMessage {
    REFRESH_FAILED,
    SAVE_FAILED
}

data class TaskListUiState(
    val tasks: List<Task> = emptyList(),
    val visibleTasks: List<Task> = emptyList(),
    val isLoading: Boolean = false,
    val loadError: Boolean = false,
    val filter: TaskFilter = TaskFilter.ALL,
    val query: String = ""
)

class TaskListViewModel(
    private val repository: TaskRepository,
    private val endOfTodayProvider: () -> Long
) : ViewModel() {

    constructor() : this(
        repository = RemoteTaskRepository(),
        endOfTodayProvider = ::endOfTodayMillis
    )

    private val _uiState = MutableLiveData(TaskListUiState(isLoading = true))
    val uiState: LiveData<TaskListUiState> = _uiState

    private val _message = MutableLiveData<UiMessage?>()
    val message: LiveData<UiMessage?> = _message

    init {
        loadTasks()
    }

    fun loadTasks() {
        val current = _uiState.value ?: TaskListUiState()
        publish(current.copy(isLoading = true, loadError = false))

        viewModelScope.launch {
            repository.getTasks()
                .onSuccess { tasks ->
                    val latest = _uiState.value ?: TaskListUiState()
                    publish(
                        latest.copy(
                            tasks = tasks,
                            isLoading = false,
                            loadError = false
                        )
                    )
                }
                .onFailure {
                    val latest = _uiState.value ?: TaskListUiState()
                    publish(
                        latest.copy(
                            isLoading = false,
                            loadError = latest.tasks.isEmpty()
                        )
                    )
                    if (latest.tasks.isNotEmpty()) {
                        _message.value = UiMessage.REFRESH_FAILED
                    }
                }
        }
    }

    fun setFilter(filter: TaskFilter) {
        val current = _uiState.value ?: TaskListUiState()
        publish(current.copy(filter = filter))
    }

    fun setQuery(query: String) {
        val current = _uiState.value ?: TaskListUiState()
        publish(current.copy(query = query))
    }

    fun addTask(draft: TaskDraft) {
        viewModelScope.launch {
            repository.addTask(draft)
                .onSuccess { created ->
                    val current = _uiState.value ?: TaskListUiState()
                    publish(current.copy(tasks = current.tasks + created, loadError = false))
                }
                .onFailure { _message.value = UiMessage.SAVE_FAILED }
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
                .onSuccess { updated ->
                    val current = _uiState.value ?: TaskListUiState()
                    publish(
                        current.copy(
                            tasks = current.tasks.map {
                                if (it.id == updated.id) updated else it
                            }
                        )
                    )
                }
                .onFailure { _message.value = UiMessage.SAVE_FAILED }
        }
    }

    fun setCompleted(task: Task, completed: Boolean) {
        val previous = _uiState.value ?: return
        val optimistic = task.copy(completed = completed)
        publish(
            previous.copy(
                tasks = previous.tasks.map {
                    if (it.id == task.id) optimistic else it
                }
            )
        )

        viewModelScope.launch {
            repository.updateTask(optimistic)
                .onSuccess { updated ->
                    val current = _uiState.value ?: TaskListUiState()
                    publish(
                        current.copy(
                            tasks = current.tasks.map {
                                if (it.id == updated.id) updated else it
                            }
                        )
                    )
                }
                .onFailure {
                    val current = _uiState.value ?: previous
                    publish(
                        current.copy(
                            tasks = current.tasks.map {
                                if (it.id == task.id) task else it
                            }
                        )
                    )
                    _message.value = UiMessage.SAVE_FAILED
                }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task.id)
                .onSuccess {
                    val current = _uiState.value ?: TaskListUiState()
                    publish(current.copy(tasks = current.tasks.filterNot { it.id == task.id }))
                }
                .onFailure { _message.value = UiMessage.SAVE_FAILED }
        }
    }

    fun messageShown() {
        _message.value = null
    }

    private fun publish(state: TaskListUiState) {
        val sorted = state.tasks.sortedWith(
            compareBy<Task> { it.completed }
                .thenBy { it.dueAt ?: Long.MAX_VALUE }
                .thenByDescending { it.createdAt }
        )
        val filtered = when (state.filter) {
            TaskFilter.ALL -> sorted
            TaskFilter.TODAY -> sorted.filter {
                !it.completed && it.dueAt != null && it.dueAt <= endOfTodayProvider()
            }
            TaskFilter.UPCOMING -> sorted.filter {
                !it.completed && it.dueAt != null && it.dueAt > endOfTodayProvider()
            }
            TaskFilter.COMPLETED -> sorted.filter { it.completed }
        }
        val query = state.query.trim().lowercase(Locale.ROOT)
        val visible = if (query.isEmpty()) {
            filtered
        } else {
            filtered.filter {
                it.title.lowercase(Locale.ROOT).contains(query) ||
                    it.notes.lowercase(Locale.ROOT).contains(query) ||
                    it.category.lowercase(Locale.ROOT).contains(query)
            }
        }

        _uiState.value = state.copy(visibleTasks = visible)
    }
}

private fun endOfTodayMillis(): Long = Calendar.getInstance().run {
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 999)
    timeInMillis
}
