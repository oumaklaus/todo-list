package com.example.taskflow.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.taskflow.data.Priority
import com.example.taskflow.data.Task
import com.example.taskflow.data.TaskDraft
import com.example.taskflow.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeTaskRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeTaskRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load exposes sorted tasks`() = kotlinx.coroutines.test.runTest(dispatcher) {
        repository.tasksResult = Result.success(
            listOf(
                task("undated", "Undated", dueAt = null, createdAt = 300),
                task("done", "Done", dueAt = 500, completed = true, createdAt = 400),
                task("soon", "Soon", dueAt = 1000, createdAt = 100),
                task("later", "Later", dueAt = 1500, createdAt = 200)
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            listOf("Soon", "Later", "Undated", "Done"),
            viewModel.uiState.value!!.visibleTasks.map { it.title }
        )
        assertFalse(viewModel.uiState.value!!.isLoading)
        assertFalse(viewModel.uiState.value!!.loadError)
    }

    @Test
    fun `initial load failure shows full page error`() = kotlinx.coroutines.test.runTest(dispatcher) {
        repository.tasksResult = Result.failure(IllegalStateException("offline"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value!!.loadError)
        assertFalse(viewModel.uiState.value!!.isLoading)
    }

    @Test
    fun `refresh failure keeps current tasks and emits message`() = kotlinx.coroutines.test.runTest(dispatcher) {
        repository.tasksResult = Result.success(listOf(task("1", "Keep me")))
        val viewModel = createViewModel()
        advanceUntilIdle()
        repository.tasksResult = Result.failure(IllegalStateException("offline"))

        viewModel.loadTasks()
        advanceUntilIdle()

        assertEquals(listOf("Keep me"), viewModel.uiState.value!!.visibleTasks.map { it.title })
        assertEquals(UiMessage.REFRESH_FAILED, viewModel.message.value)
    }

    @Test
    fun `today filter includes active overdue and today tasks`() = kotlinx.coroutines.test.runTest(dispatcher) {
        repository.tasksResult = Result.success(
            listOf(
                task("overdue", "Overdue", dueAt = 500),
                task("today", "Today", dueAt = 2000),
                task("future", "Future", dueAt = 2001),
                task("done", "Done", dueAt = 1000, completed = true),
                task("none", "No date")
            )
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setFilter(TaskFilter.TODAY)

        assertEquals(
            listOf("Overdue", "Today"),
            viewModel.uiState.value!!.visibleTasks.map { it.title }
        )
    }

    @Test
    fun `upcoming and completed filters select matching tasks`() = kotlinx.coroutines.test.runTest(dispatcher) {
        repository.tasksResult = Result.success(
            listOf(
                task("today", "Today", dueAt = 2000),
                task("future", "Future", dueAt = 2001),
                task("done", "Done", completed = true)
            )
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setFilter(TaskFilter.UPCOMING)
        assertEquals(listOf("Future"), viewModel.uiState.value!!.visibleTasks.map { it.title })

        viewModel.setFilter(TaskFilter.COMPLETED)
        assertEquals(listOf("Done"), viewModel.uiState.value!!.visibleTasks.map { it.title })
    }

    @Test
    fun `search matches title notes and category ignoring case`() = kotlinx.coroutines.test.runTest(dispatcher) {
        repository.tasksResult = Result.success(
            listOf(
                task("1", "Math assignment", notes = "", category = "School", createdAt = 100),
                task("2", "Buy paper", notes = "for MATH class", category = "Errands", createdAt = 200),
                task("3", "Call home", notes = "", category = "Math", createdAt = 300)
            )
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setQuery("math")

        assertEquals(
            listOf("Call home", "Buy paper", "Math assignment"),
            viewModel.uiState.value!!.visibleTasks.map { it.title }
        )
    }

    @Test
    fun `add task appends created task`() = kotlinx.coroutines.test.runTest(dispatcher) {
        val created = task("2", "New task")
        repository.addResult = Result.success(created)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.addTask(TaskDraft("New task"))
        advanceUntilIdle()

        assertEquals(listOf(created), viewModel.uiState.value!!.visibleTasks)
    }

    @Test
    fun `update task replaces matching task`() = kotlinx.coroutines.test.runTest(dispatcher) {
        val original = task("1", "Draft")
        val updated = original.copy(title = "Final", updatedAt = 200)
        repository.tasksResult = Result.success(listOf(original))
        repository.updateResult = Result.success(updated)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateTask(updated)
        advanceUntilIdle()

        assertEquals("Final", viewModel.uiState.value!!.visibleTasks.single().title)
    }

    @Test
    fun `completion failure rolls back optimistic state`() = kotlinx.coroutines.test.runTest(dispatcher) {
        val original = task("1", "Task")
        repository.tasksResult = Result.success(listOf(original))
        repository.updateResult = Result.failure(IllegalStateException("offline"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setCompleted(original, true)
        assertTrue(viewModel.uiState.value!!.tasks.single().completed)
        viewModel.setQuery("Task")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value!!.tasks.single().completed)
        assertEquals("Task", viewModel.uiState.value!!.query)
        assertEquals(UiMessage.SAVE_FAILED, viewModel.message.value)
    }

    @Test
    fun `delete task removes it after success`() = kotlinx.coroutines.test.runTest(dispatcher) {
        val original = task("1", "Task")
        repository.tasksResult = Result.success(listOf(original))
        repository.deleteResult = Result.success(Unit)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteTask(original)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value!!.tasks.isEmpty())
    }

    private fun createViewModel(): TaskListViewModel = TaskListViewModel(
        repository = repository,
        endOfTodayProvider = { 2000L }
    )

    private fun task(
        id: String,
        title: String,
        notes: String = "",
        dueAt: Long? = null,
        category: String = "",
        completed: Boolean = false,
        createdAt: Long = 100
    ): Task = Task(
        id = id,
        title = title,
        notes = notes,
        dueAt = dueAt,
        priority = Priority.MEDIUM,
        category = category,
        completed = completed,
        createdAt = createdAt,
        updatedAt = createdAt
    )
}

private class FakeTaskRepository : TaskRepository {
    var tasksResult: Result<List<Task>> = Result.success(emptyList())
    var addResult: Result<Task> = Result.failure(IllegalStateException("No add result"))
    var updateResult: Result<Task> = Result.failure(IllegalStateException("No update result"))
    var deleteResult: Result<Unit> = Result.failure(IllegalStateException("No delete result"))

    override suspend fun getTasks(): Result<List<Task>> = tasksResult

    override suspend fun addTask(draft: TaskDraft): Result<Task> = addResult

    override suspend fun updateTask(task: Task): Result<Task> = updateResult

    override suspend fun deleteTask(id: String): Result<Unit> = deleteResult
}
