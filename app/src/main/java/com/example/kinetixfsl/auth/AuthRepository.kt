package com.example.kinetixfsl.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
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

    suspend fun signIn(email: String, password: String): AuthResult {
        return try {
            firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
            AuthResult.Success
        } catch (e: FirebaseAuthInvalidUserException) {
            // No account for this email, or it's disabled.
            AuthResult.Error("No account found with this email.")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            // Wrong password or malformed email.
            AuthResult.Error("Incorrect email or password.")
        } catch (e: Exception) {
            // Network failure or anything unexpected. Keep it human.
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

            // Attach the full name to the freshly created user.
            authResult.user?.updateProfile(
                userProfileChangeRequest { displayName = fullName.trim() }
            )?.await()

            AuthResult.Success
        } catch (e: FirebaseAuthUserCollisionException) {
            // Email already registered.
            AuthResult.Error("An account with this email already exists.")
        } catch (e: FirebaseAuthWeakPasswordException) {
            // Firebase requires 6+ chars by default.
            AuthResult.Error("Password is too weak. Use at least 6 characters.")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            // Malformed email.
            AuthResult.Error("That email address looks invalid.")
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Something went wrong. Try again.")
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
    }
}