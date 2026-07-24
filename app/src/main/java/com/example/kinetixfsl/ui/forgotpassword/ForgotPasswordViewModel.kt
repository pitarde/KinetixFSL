package com.example.kinetixfsl.ui.forgotpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.auth.AuthRepository
import com.example.kinetixfsl.auth.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the forgot-password screen needs to render, in one immutable object. */
data class ForgotPasswordUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** True once Firebase accepts the request; the UI navigates on to the confirmation. */
    val isRequestSent: Boolean = false,
    /** Preserved for the confirmation screen so it can echo which address we sent to. */
    val sentToEmail: String = "",
)

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, errorMessage = null) }
    }

    fun sendResetEmail() {
        val email = _uiState.value.email.trim()

        // Local checks first — cheaper than a network round trip.
        if (email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your email.") }
            return
        }
        if (!email.contains("@") || !email.contains(".")) {
            _uiState.update { it.copy(errorMessage = "That email address looks invalid.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = authRepository.sendPasswordResetEmail(email)) {
                is AuthResult.Success ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRequestSent = true,
                            sentToEmail = email,
                        )
                    }

                is AuthResult.Error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    /** Called after the UI navigates to the confirmation screen. */
    fun onRequestHandled() {
        _uiState.update { it.copy(isRequestSent = false) }
    }
}