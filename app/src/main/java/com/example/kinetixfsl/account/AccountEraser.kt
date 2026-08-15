package com.example.kinetixfsl.account

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Wipes a user's data — the "Reset" and "Delete account" actions in Settings.
 *
 * Best-effort and client-side: each step is guarded, so one failure never
 * aborts the rest. It deletes everything the security rules let a user delete
 * for themselves (their local progress, their `progress/{uid}` doc, their own
 * posts + the votes/shares/comments under them, communities they created, and
 * their notifications).
 *
 * ## What it deliberately does NOT delete (rule-limited)
 *
 * The rules block a client from deleting `users/{uid}` (their public profile)
 * and conversation documents (`allow delete: if false`). Fully removing those
 * needs a Cloud Function with the Admin SDK. For now the profile row lingers
 * after a full delete; loosen the users `delete` rule or add a Function later
 * for a truly complete wipe.
 */
class AccountEraser(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {

    /** Outcome of a full delete, so the UI can tell the user what happened. */
    enum class DeleteOutcome { DELETED, DATA_WIPED_NEEDS_REAUTH, FAILED }

    /**
     * Reset: wipe all of this account's data but keep the login alive. After it
     * returns, restart/recreate the UI so screens re-read the empty stores.
     */
    suspend fun resetData(context: Context): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        wipeEverything(context, uid)
    }

    /**
     * Full delete: wipe the data AND remove the Firebase Auth account. Data is
     * wiped first, while still authenticated. Deleting the login can fail if the
     * session is old ("recent login required") — the data is still gone, and the
     * UI should ask the user to sign in again and retry.
     */
    suspend fun deleteAccount(context: Context): DeleteOutcome {
        val user = auth.currentUser ?: return DeleteOutcome.FAILED
        val uid = user.uid
        runCatching { wipeEverything(context, uid) }
            .onFailure { Log.w(TAG, "data wipe had failures (continuing)", it) }
        return try {
            user.delete().await()
            DeleteOutcome.DELETED
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            DeleteOutcome.DATA_WIPED_NEEDS_REAUTH
        } catch (e: Exception) {
            Log.e(TAG, "auth delete failed", e)
            DeleteOutcome.FAILED
        }
    }

    // ── the wipe ────────────────────────────────────────────────────────────

    private suspend fun wipeEverything(context: Context, uid: String) {
        clearLocalProgress(context, uid)
        runCatching { db.collection(PROGRESS).document(uid).delete().await() }
            .onFailure { Log.w(TAG, "progress doc delete failed", it) }
        deleteOwnPosts(uid)
        deleteOwnCommunities(uid)
        deleteNotifications(uid)
    }

    /** Clears the per-account progress, activity and quiz SharedPreferences. */
    private fun clearLocalProgress(context: Context, uid: String) {
        listOf("kinetix_progress__$uid", "kinetix_activity__$uid", "quiz_game__$uid")
            .forEach { name ->
                runCatching {
                    context.applicationContext
                        .getSharedPreferences(name, Context.MODE_PRIVATE)
                        .edit().clear().apply()
                }
            }
    }

    /** Deletes every post the user authored, tree-first (comments/votes/shares). */
    private suspend fun deleteOwnPosts(uid: String) = runCatching {
        val posts = db.collection(POSTS).whereEqualTo("authorId", uid).get().await()
        for (doc in posts.documents) {
            val ref = doc.reference
            deleteAllIn(ref.collection(COMMENTS))
            deleteAllIn(ref.collection(VOTES))
            deleteAllIn(ref.collection(SHARES))
            runCatching { ref.delete().await() }
        }
    }.onFailure { Log.w(TAG, "own-posts delete failed", it) }.let { }

    /** Deletes communities this user created, members first. */
    private suspend fun deleteOwnCommunities(uid: String) = runCatching {
        val comms = db.collection(COMMUNITIES).whereEqualTo("creatorId", uid).get().await()
        for (doc in comms.documents) {
            deleteAllIn(doc.reference.collection(MEMBERS))
            runCatching { doc.reference.delete().await() }
        }
    }.onFailure { Log.w(TAG, "own-communities delete failed", it) }.let { }

    /** Empties this user's notification inbox. */
    private suspend fun deleteNotifications(uid: String) = runCatching {
        deleteAllIn(db.collection(NOTIFICATIONS).document(uid).collection(ITEMS))
    }.onFailure { Log.w(TAG, "notifications delete failed", it) }.let { }

    /** Deletes a whole (sub)collection in batches; best-effort. */
    private suspend fun deleteAllIn(collection: CollectionReference) {
        try {
            while (true) {
                val snap = collection.limit(BATCH.toLong()).get().await()
                if (snap.isEmpty) break
                for (d in snap.documents) runCatching { d.reference.delete().await() }
                if (snap.size() < BATCH) break
            }
        } catch (e: Exception) {
            Log.w(TAG, "batch delete of ${collection.path} failed", e)
        }
    }

    private companion object {
        const val TAG = "AccountEraser"
        const val PROGRESS = "progress"
        const val POSTS = "posts"
        const val VOTES = "votes"
        const val SHARES = "shares"
        const val COMMENTS = "comments"
        const val COMMUNITIES = "communities"
        const val MEMBERS = "members"
        const val NOTIFICATIONS = "notifications"
        const val ITEMS = "items"
        const val BATCH = 300
    }
}
