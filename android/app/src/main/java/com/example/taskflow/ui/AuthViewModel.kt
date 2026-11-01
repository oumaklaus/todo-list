package com.example.taskflow.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskflow.AppContainer
import com.example.taskflow.data.AuthMode
import com.example.taskflow.data.AuthRepository
import com.example.taskflow.data.AuthResult
import com.example.taskflow.data.SessionStore
import kotlinx.coroutines.launch

data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val isLoading: Boolean = false,
    val authenticated: Boolean = false,
    val displayNameError: AuthInputError? = null,
    val emailError: AuthInputError? = null,
    val passwordError: AuthInputError? = null,
    val message: String? = null
)

class AuthViewModel(
    private val repository: AuthRepository,
    private val sessionStore: SessionStore
) : ViewModel() {
    constructor() : this(AppContainer.authRepository, AppContainer.sessionStore)

    private val _uiState = MutableLiveData(AuthUiState())
    val uiState: LiveData<AuthUiState> = _uiState

    fun setMode(mode: AuthMode) {
        _uiState.value = AuthUiState(mode = mode)
    }

    fun submit(displayName: String, email: String, password: String) {
        val current = _uiState.value ?: AuthUiState()
        if (current.isLoading) return

        when (val validation = AuthInputValidator.validate(
            current.mode,
            displayName,
            email,
            password
        )) {
            is AuthInputResult.Invalid -> {
                _uiState.value = current.copy(
                    displayNameError = validation.displayNameError,
                    emailError = validation.emailError,
                    passwordError = validation.passwordError,
                    message = null
                )
            }
            is AuthInputResult.Valid -> authenticate(current.mode, validation)
        }
    }

    private fun authenticate(mode: AuthMode, input: AuthInputResult.Valid) {
        _uiState.value = AuthUiState(mode = mode, isLoading = true)
        viewModelScope.launch {
            when (val result = repository.authenticate(
                mode,
                input.displayName,
                input.email,
                input.password
            )) {
                is AuthResult.Success -> {
                    sessionStore.save(result.auth)
                    _uiState.value = AuthUiState(mode = mode, authenticated = true)
                }
                is AuthResult.Failure -> {
                    _uiState.value = AuthUiState(mode = mode, message = result.message)
                }
            }
        }
    }
}
