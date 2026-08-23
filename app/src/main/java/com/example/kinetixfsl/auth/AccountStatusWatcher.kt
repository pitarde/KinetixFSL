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

            // Drop any block message from the PREVIOUS account. Without this a
            // notice raised for account A lingered in the flow and blocked the
            // very next sign-in — even a different, perfectly fine account.
            _blockMessage.value = null

            val uid = a.currentUser?.uid ?: return@AuthStateListener

            // Per-session baseline of `wipedAt`. Established on the first
            // snapshot after this sign-in; we only force a logout when it grows
            // AFTER that (i.e. an admin wiped the account while the user was
            // already in the app). A wipe that was already present at sign-in is
            // handled silently by ProgressSync.applyRemoteWipeIfNeeded, so a
            // fresh login never gets kicked — only an active session does.
            var wipeBaseline = -1L

            registration = FirebaseFirestore.getInstance()
                .collection("accountStatus").document(uid)
                .addSnapshotListener { snap, err ->
                    if (err != null) { Log.w(TAG, "status listen failed", err); return@addSnapshotListener }
                    // Only act on the account that is still the current one — a
                    // late snapshot for a signed-out account must not block.
                    if (FirebaseAuth.getInstance().currentUser?.uid != uid) return@addSnapshotListener
                    if (snap == null || !snap.exists()) return@addSnapshotListener
                    // Ignore cache-only reads. After an admin re-enables an
                    // account, the device may still hold the old `disabled:true`
                    // in its offline cache; acting on that stale value would kick
                    // the user out again the instant they sign back in. Only the
                    // server-confirmed value is authoritative for a block.
                    if (snap.metadata.isFromCache) return@addSnapshotListener

                    val wipedAtMs = snap.getTimestamp("wipedAt")?.toDate()?.time ?: 0L
                    val firstSnapshot = wipeBaseline < 0L
                    if (firstSnapshot) wipeBaseline = wipedAtMs

                    val disabled = snap.getBoolean("disabled") == true
                    val until = snap.getTimestamp("lockedUntil")
                    val reason = snap.getString("reason")?.takeIf { it.isNotBlank() }
                    val locked = until != null && until > Timestamp.now()

                    if (disabled || locked) {
                        _blockMessage.value = buildMessage(disabled, until, reason)
                        return@addSnapshotListener
                    }

                    // Data wiped DURING this session (wipedAt grew past the
                    // baseline): force logout with a notice, like disable/penalty
                    // — but NOT a ban. No disabled/lockedUntil is set, so the next
                    // sign-in succeeds and starts fresh.
                    if (!firstSnapshot && wipedAtMs > wipeBaseline) {
                        wipeBaseline = wipedAtMs
                        _blockMessage.value = buildString {
                            append("Your account has been deleted by an administrator.")
                            if (reason != null) append(" Reason: $reason.")
                            append(" As a penalty, all your data has been removed. You may sign in again, but you'll start over from scratch.")
                        }
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
