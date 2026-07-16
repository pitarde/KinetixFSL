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
 *
 * Firebase's own calls are callback-based; we bridge them to coroutines with
 * `.await()` so the ViewModel can call them like normal suspend functions.
 */
class AuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    /** The signed-in user, or null if nobody is signed in. */
    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    /** True if a user session already exists (used to skip login on relaunch). */
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

    /**
     * Creates a new account, then stamps the display name onto it so the rest of
     * the app can greet the user by name. On success the user is also signed in.
     */
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

    // ---------------------------------------------------------------------------------
    // Google
    // ---------------------------------------------------------------------------------

    /**
     * Runs the full Google Sign-In flow via Credential Manager, then exchanges
     * the resulting Google ID token for a Firebase session.
     *
     * If Firebase has never seen this Google account before, it creates one; if
     * it has, it signs in as before. Either way, on success [isSignedIn] is true.
     *
     * The [context] should be an Activity context so the credential sheet can render.
     */
    suspend fun signInWithGoogle(context: Context): AuthResult {
        // 1. Ask Credential Manager for a Google ID token.
        val idToken = when (val tokenResult = requestGoogleIdToken(context)) {
            is GoogleTokenResult.Success -> tokenResult.idToken
            is GoogleTokenResult.Failure -> return AuthResult.Error(tokenResult.message)
        }

        // 2. Exchange the ID token for a Firebase user session.
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

            // setFilterByAuthorizedAccounts(false) so first-time users see all
            // Google accounts on the device, not only ones already linked to us.
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
            // User tapped away / closed the sheet — not really an error.
            GoogleTokenResult.Failure("Sign-in cancelled.")
        } catch (e: NoCredentialException) {
            // No Google account on the device.
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