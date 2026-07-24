package com.example.kinetixfsl.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.tasks.await

/**
 * The single point of contact with Firebase Authentication. Everything else in
 * the app talks to this, never to FirebaseAuth directly — so if the backend ever
 * changes, only this file does.
 */
class AuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    val isSignedIn: Boolean
        get() = firebaseAuth.currentUser != null

    // ---------------------------------------------------------------------------------
    // Email / password
    // ---------------------------------------------------------------------------------

    suspend fun signIn(email: String, password: String): AuthResult {
        return try {
            firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
            AuthResult.Success
        } catch (e: FirebaseAuthInvalidUserException) {
            AuthResult.Error("No account found with this email.")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            AuthResult.Error("Incorrect email or password.")
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Something went wrong. Try again.")
        }
    }

    suspend fun signUp(fullName: String, email: String, password: String): AuthResult {
        return try {
            val authResult = firebaseAuth
                .createUserWithEmailAndPassword(email.trim(), password)
                .await()

            authResult.user?.updateProfile(
                userProfileChangeRequest { displayName = fullName.trim() }
            )?.await()

            AuthResult.Success
        } catch (e: FirebaseAuthUserCollisionException) {
            AuthResult.Error("An account with this email already exists.")
        } catch (e: FirebaseAuthWeakPasswordException) {
            AuthResult.Error("Password is too weak. Use at least 6 characters.")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            AuthResult.Error("That email address looks invalid.")
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Something went wrong. Try again.")
        }
    }

    /**
     * Asks Firebase to email a password-reset link to [email]. Firebase hosts
     * the reset page itself — the user taps the link, sets a new password in
     * their browser, then returns to the app and logs in normally.
     *
     * IMPORTANT: for privacy, Firebase does NOT tell us whether the email is
     * actually registered. It returns success either way (or a network error).
     * That means we cannot show a "no such account" message here without
     * leaking whether an email is in the system — which would be an
     * account-enumeration risk. We say "check your inbox" and move on.
     */
    suspend fun sendPasswordResetEmail(email: String): AuthResult {
        return try {
            firebaseAuth.sendPasswordResetEmail(email.trim()).await()
            AuthResult.Success
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            AuthResult.Error("That email address looks invalid.")
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Something went wrong. Try again.")
        }
    }

    // ---------------------------------------------------------------------------------
    // Google
    // ---------------------------------------------------------------------------------

    suspend fun signInWithGoogle(context: Context): AuthResult {
        val idToken = when (val tokenResult = requestGoogleIdToken(context)) {
            is GoogleTokenResult.Success -> tokenResult.idToken
            is GoogleTokenResult.Failure -> return AuthResult.Error(tokenResult.message)
        }

        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth.signInWithCredential(credential).await()
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Firebase sign-in failed.")
        }
    }

    private suspend fun requestGoogleIdToken(context: Context): GoogleTokenResult {
        return try {
            val credentialManager = CredentialManager.create(context)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(context, request)
            val credential = response.credential

            if (
                credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleTokenResult.Success(googleCred.idToken)
            } else {
                GoogleTokenResult.Failure("Unexpected credential type from Google.")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleTokenResult.Failure("Sign-in cancelled.")
        } catch (e: NoCredentialException) {
            GoogleTokenResult.Failure("No Google account available on this device.")
        } catch (e: GoogleIdTokenParsingException) {
            GoogleTokenResult.Failure("Could not read Google credentials.")
        } catch (e: GetCredentialException) {
            GoogleTokenResult.Failure(e.localizedMessage ?: "Google sign-in failed.")
        }
    }

    // ---------------------------------------------------------------------------------

    fun signOut() {
        firebaseAuth.signOut()
    }

    private sealed interface GoogleTokenResult {
        data class Success(val idToken: String) : GoogleTokenResult
        data class Failure(val message: String) : GoogleTokenResult
    }

    private companion object {
        // The Web Client ID from Firebase console → Authentication → Google →
        // Web SDK configuration. Not a secret — it ships in every Google-Sign-In
        // Android app. Shipping this in-source is fine and matches Google's guidance.
        const val WEB_CLIENT_ID =
            "173560349034-6dvgp9urpkr5sjc662acmcss17sie8ug.apps.googleusercontent.com"
    }
}