package com.example.kinetixfsl.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.auth.AuthRepository
import com.example.kinetixfsl.auth.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** All the state the register screen renders from, in one immutable object. */
data class RegisterUiState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmVisible: Boolean = false,
    val agreedToTerms: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isRegisterSuccessful: Boolean = false,
)

class RegisterViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onFullNameChange(value: String) =
        _uiState.update { it.copy(fullName = value, errorMessage = null) }

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value, errorMessage = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, errorMessage = null) }

    fun onConfirmPasswordChange(value: String) =
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }

    fun togglePasswordVisibility() =
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }

    fun toggleConfirmVisibility() =
        _uiState.update { it.copy(isConfirmVisible = !it.isConfirmVisible) }

    fun toggleAgreedToTerms() =
        _uiState.update { it.copy(agreedToTerms = !it.agreedToTerms, errorMessage = null) }

    fun register() {
        val state = _uiState.value

        // Validate locally before touching the network, top to bottom.
        val validationError = when {
            state.fullName.isBlank() -> "Please enter your full name."
            state.email.isBlank() -> "Please enter your email."
            state.password.isBlank() -> "Please enter a password."
            state.password.length < 6 -> "Password must be at least 6 characters."
            state.confirmPassword != state.password -> "Passwords do not match."
            !state.agreedToTerms -> "Please agree to the Terms of Service and Privacy Policy."
            else -> null
        }
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = authRepository.signUp(state.fullName, state.email, state.password)) {
                is AuthResult.Success ->
                    _uiState.update { it.copy(isLoading = false, isRegisterSuccessful = true) }

                is AuthResult.Error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    /** Called by the UI after it has navigated away, so re-entry starts clean. */
    fun onRegisterHandled() {
        _uiState.update { it.copy(isRegisterSuccessful = false) }
    }
}