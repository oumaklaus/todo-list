package com.example.taskflow

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.taskflow.data.AuthMode
import com.example.taskflow.databinding.ActivityLoginBinding
import com.example.taskflow.ui.AuthInputError
import com.example.taskflow.ui.AuthViewModel

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AppContainer.sessionStore.token.isNullOrBlank()) {
            openTasks()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonSubmit.setOnClickListener { submit() }
        binding.buttonSwitchMode.setOnClickListener {
            val nextMode = if (viewModel.uiState.value?.mode == AuthMode.REGISTER) {
                AuthMode.LOGIN
            } else {
                AuthMode.REGISTER
            }
            viewModel.setMode(nextMode)
        }
        binding.inputPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }

        viewModel.uiState.observe(this) { state ->
            val registering = state.mode == AuthMode.REGISTER
            binding.layoutDisplayName.isVisible = registering
            binding.textTitle.setText(
                if (registering) R.string.create_account_title else R.string.login_title
            )
            binding.textSubtitle.setText(
                if (registering) R.string.create_account_subtitle else R.string.login_subtitle
            )
            binding.buttonSubmit.setText(
                if (registering) R.string.create_account else R.string.log_in
            )
            binding.buttonSwitchMode.setText(
                if (registering) R.string.have_account_log_in else R.string.new_account_sign_up
            )

            binding.layoutDisplayName.error = state.displayNameError?.message()
            binding.layoutEmail.error = state.emailError?.message()
            binding.layoutPassword.error = state.passwordError?.message()
            binding.textAuthError.text = state.message
            binding.textAuthError.isVisible = !state.message.isNullOrBlank()
            binding.progressAuth.isVisible = state.isLoading
            binding.buttonSubmit.isEnabled = !state.isLoading
            binding.buttonSwitchMode.isEnabled = !state.isLoading

            if (state.authenticated) openTasks()
        }
    }

    private fun submit() {
        viewModel.submit(
            binding.inputDisplayName.text?.toString().orEmpty(),
            binding.inputEmail.text?.toString().orEmpty(),
            binding.inputPassword.text?.toString().orEmpty()
        )
    }

    private fun AuthInputError.message(): String = getString(
        when (this) {
            AuthInputError.DISPLAY_NAME_REQUIRED -> R.string.error_display_name_required
            AuthInputError.DISPLAY_NAME_TOO_LONG -> R.string.error_display_name_too_long
            AuthInputError.EMAIL_INVALID -> R.string.error_email_invalid
            AuthInputError.PASSWORD_TOO_SHORT -> R.string.error_password_too_short
            AuthInputError.PASSWORD_TOO_LONG -> R.string.error_password_too_long
        }
    )

    private fun openTasks() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
