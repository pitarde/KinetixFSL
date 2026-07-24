package com.example.kinetixfsl.auth

/**
 * The outcome of an auth attempt. Using a sealed type (instead of throwing)
 * keeps the ViewModel's logic explicit: every call site must handle both cases.
 */
sealed interface AuthResult {
    data object Success : AuthResult
    data class Error(val message: String) : AuthResult
}