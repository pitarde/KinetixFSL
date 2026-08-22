package com.example.kinetixfsl.auth

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.DateFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watches the signed-in learner's `accountStatus/{uid}` document live, so an
 * admin disabling or time-penalising the account takes effect *immediately* —
 * the learner is kicked out mid-session, not only at their next sign-in.
 *
 * [AuthRepository.enforceAccountStatus] handles the sign-in-time check; this
 * handles the "already in the app when the admin acts" case. When a block is
 * detected it publishes a message on [blockMessage]; the navigation layer
 * observes it, signs the user out, shows the message and returns them to Login.
 */
object AccountStatusWatcher {

    private const val TAG = "AccountStatusWatcher"

    private val _blockMessage = MutableStateFlow<String?>(null)
    /** Non-null when the current account has just been blocked; the UI consumes it. */
    val blockMessage: StateFlow<String?> = _blockMessage.asStateFlow()

    private var registration: ListenerRegistration? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var started = false

    /** Begin watching. Safe to call once from the Activity's onCreate. */
    fun start() {
        if (started) return
        started = true
        val auth = FirebaseAuth.getInstance()
        authListener = FirebaseAuth.AuthStateListener { a ->
            // Re-point the listener whenever the signed-in account changes.
            registration?.remove()
            registration = null
            val uid = a.currentUser?.uid ?: return@AuthStateListener
            registration = FirebaseFirestore.getInstance()
                .collection("accountStatus").document(uid)
                .addSnapshotListener { snap, err ->
                    if (err != null) { Log.w(TAG, "status listen failed", err); return@addSnapshotListener }
                    if (snap == null || !snap.exists()) return@addSnapshotListener

                    val disabled = snap.getBoolean("disabled") == true
                    val until = snap.getTimestamp("lockedUntil")
                    val reason = snap.getString("reason")?.takeIf { it.isNotBlank() }
                    val locked = until != null && until > Timestamp.now()

                    if (disabled || locked) {
                        _blockMessage.value = buildMessage(disabled, until, reason)
                    }
                }
        }
        auth.addAuthStateListener(authListener!!)
    }

    /** Called by the UI after it has handled a block, so it doesn't re-fire. */
    fun consume() {
        _blockMessage.value = null
    }

    private fun buildMessage(disabled: Boolean, until: Timestamp?, reason: String?): String = when {
        disabled -> buildString {
            append("Your account has been disabled.")
            if (reason != null) append(" Reason: $reason.")
            append(" Contact support if you think this is a mistake.")
        }
        else -> buildString {
            val whenStr = until?.toDate()?.let {
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it)
            } ?: "later"
            append("You're temporarily restricted until $whenStr.")
            if (reason != null) append(" Reason: $reason.")
        }
    }
}
