package com.example.taskflow.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.taskflow.data.AuthMode
import com.example.taskflow.data.AuthRepository
import com.example.taskflow.data.AuthResponse
import com.example.taskflow.data.AuthResult
import com.example.taskflow.data.AuthUser
import com.example.taskflow.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeAuthRepository
    private lateinit var session: FakeSessionStore
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeAuthRepository()
        session = FakeSessionStore()
        viewModel = AuthViewModel(repository, session)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `invalid registration publishes field errors without calling api`() {
        viewModel.setMode(AuthMode.REGISTER)

        viewModel.submit("", "not-an-email", "short")

        val state = viewModel.uiState.value!!
        assertEquals(AuthInputError.DISPLAY_NAME_REQUIRED, state.displayNameError)
        assertEquals(AuthInputError.EMAIL_INVALID, state.emailError)
        assertEquals(AuthInputError.PASSWORD_TOO_SHORT, state.passwordError)
        assertEquals(0, repository.calls)
    }

    @Test
    fun `successful login saves session and requests navigation`() = runTest(dispatcher) {
        val auth = AuthResponse("token-123", AuthUser("user-1", "Ada", "ada@example.com"))
        repository.result = AuthResult.Success(auth)

        viewModel.submit("", " ADA@Example.COM ", "correct horse")
        advanceUntilIdle()

        assertEquals(auth, session.saved)
        assertTrue(viewModel.uiState.value!!.authenticated)
        assertEquals(AuthMode.LOGIN, repository.mode)
        assertEquals("ada@example.com", repository.email)
        assertFalse(viewModel.uiState.value!!.isLoading)
    }

    @Test
    fun `api failure remains on form and shows message`() = runTest(dispatcher) {
        repository.result = AuthResult.Failure("Invalid email or password")

        viewModel.submit("", "ada@example.com", "correct horse")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value!!.authenticated)
        assertEquals("Invalid email or password", viewModel.uiState.value!!.message)
        assertNull(session.saved)
    }

    @Test
    fun `switching modes clears prior form errors`() {
        viewModel.submit("", "bad", "short")

        viewModel.setMode(AuthMode.REGISTER)

        val state = viewModel.uiState.value!!
        assertEquals(AuthMode.REGISTER, state.mode)
        assertNull(state.emailError)
        assertNull(state.passwordError)
        assertNull(state.message)
    }
}

private class FakeAuthRepository : AuthRepository {
    var result: AuthResult = AuthResult.Failure("No result")
    var calls = 0
    var mode: AuthMode? = null
    var email: String? = null

    override suspend fun authenticate(
        mode: AuthMode,
        displayName: String,
        email: String,
        password: String
    ): AuthResult {
        calls++
        this.mode = mode
        this.email = email
        return result
    }
}

private class FakeSessionStore : SessionStore {
    override var token: String? = null
    override var user: AuthUser? = null
    var saved: AuthResponse? = null

    override fun save(auth: AuthResponse) {
        saved = auth
        token = auth.token
        user = auth.user
    }

    override fun clear() {
        token = null
        user = null
    }
}
