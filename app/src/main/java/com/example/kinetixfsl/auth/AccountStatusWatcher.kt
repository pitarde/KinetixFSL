package com.example.kinetixfsl.auth

import android.content.Context
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
 * admin disabling, time-penalising or **deleting** the account takes effect
 * without waiting for the next sign-in.
 *
 * [AuthRepository.enforceAccountStatus] handles the sign-in-time check for
 * disable/penalty; this handles two more cases:
 *
 *  1. the admin acts while the learner is already in the app (a live snapshot
 *     arrives), and
 *  2. the admin **deletes** the account while the app is closed — on the next
 *     cold start the first snapshot already carries the wipe, and this still
 *     forces a logout.
 *
 * Case 2 relies on a persistent per-uid marker ([PREFS] / `logout_<uid>`): a
 * logout fires whenever the server's `wipedAt` is newer than the last one this
 * device acted on, so it happens exactly once per wipe whether the app was open
 * or shut. When it fires, a message is published on [blockMessage]; the
 * navigation layer signs the user out, shows it, and returns them to Login.
 */
object AccountStatusWatcher {

    private const val TAG = "AccountStatusWatcher"
    private const val PREFS = "kinetix_wipe"

    private val _blockMessage = MutableStateFlow<String?>(null)
    /** Non-null when the current account has just been blocked; the UI consumes it. */
    val blockMessage: StateFlow<String?> = _blockMessage.asStateFlow()

    private var appContext: Context? = null
    private var registration: ListenerRegistration? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var started = false

    /**
     * Set by [raise] so the next auth-state change (the sign-out that a cold-
     * start block triggers) doesn't immediately null the message before the UI
     * has shown it.
     */
    @Volatile
    private var suppressNextClear = false

    /**
     * Publish a block message from OUTSIDE the live listener — the cold-start
     * account-status check in `MainActivity.enforceAccountStatusNow()`, which
     * has already signed the session out. The nav layer shows it and routes to
     * Login, exactly as for a live block.
     */
    fun raise(message: String) {
        suppressNextClear = true
        _blockMessage.value = message
    }

    /** Begin watching. Safe to call once from the Activity's onCreate. */
    fun start(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        val auth = FirebaseAuth.getInstance()
        authListener = FirebaseAuth.AuthStateListener { a ->
            // Re-point the listener whenever the signed-in account changes.
            registration?.remove()
            registration = null

            // Drop any block message from the PREVIOUS account. Without this a
            // notice raised for account A lingered in the flow and blocked the
            // very next sign-in — even a different, perfectly fine account.
            // Skipped once right after raise(), whose own sign-out fires this
            // listener and would otherwise wipe the message before it's shown.
            if (suppressNextClear) suppressNextClear = false else _blockMessage.value = null

            val uid = a.currentUser?.uid ?: return@AuthStateListener

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
                    // server-confirmed value is authoritative.
                    if (snap.metadata.isFromCache) return@addSnapshotListener

                    val reason = snap.getString("reason")?.takeIf { it.isNotBlank() }
                    val disabled = snap.getBoolean("disabled") == true
                    val until = snap.getTimestamp("lockedUntil")
                    val locked = until != null && until > Timestamp.now()

                    if (disabled || locked) {
                        _blockMessage.value = buildMessage(disabled, until, reason)
                        return@addSnapshotListener
                    }

                    // Admin "delete account data": no `disabled`/`lockedUntil`
                    // (the account is free to reuse), just a `wipedAt` stamp.
                    // Force a logout once per wipe — tracked in a persistent
                    // per-uid marker so it also catches a delete that happened
                    // while the app was closed, on the next cold start.
                    val wipedAtMs = snap.getTimestamp("wipedAt")?.toDate()?.time ?: 0L
                    if (wipedAtMs <= 0L) return@addSnapshotListener

                    val prefs = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    val ackKey = "logout_$uid"
                    val acked = prefs?.getLong(ackKey, 0L) ?: 0L
                    if (wipedAtMs > acked) {
                        prefs?.edit()?.putLong(ackKey, wipedAtMs)?.apply()
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
