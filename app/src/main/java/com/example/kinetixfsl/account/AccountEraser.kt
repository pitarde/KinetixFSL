package com.example.kinetixfsl.account

import android.content.Context
import android.util.Log
import com.example.kinetixfsl.auth.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Wipes a user's data — the "Reset" and "Delete account" actions in Settings.
 *
 * Best-effort and client-side: each step is guarded, so one failure never
 * aborts the rest. It deletes everything the security rules let a user delete
 * for themselves: their local Room database rows, their `progress/{uid}` doc,
 * their own posts (with the comments/votes/shares under them), every
 * community they created (with EVERY post inside it, by any author, so a
 * deleted community's content doesn't linger in the feed), their
 * notifications, and their public `users/{uid}` profile.
 *
 * ## What it deliberately does NOT delete (rule-limited)
 *
 * The rules block a client from deleting conversation documents
 * (`allow delete: if false`) — fully removing those needs a Cloud Function
 * with the Admin SDK. Chat messages a deleted user sent therefore remain
 * visible to the other participant, attributed to a profile that no longer
 * resolves.
 */
class AccountEraser(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val authRepository: AuthRepository = AuthRepository(auth),
) {

    /** Outcome of a full delete, so the UI can tell the user what happened. */
    enum class DeleteOutcome {
        DELETED,
        DATA_WIPED_NEEDS_REAUTH,
        /** The user backed out of the "confirm it's you" reauth step. Nothing
         *  was touched — data, posts and the Auth account are all intact. */
        REAUTH_CANCELLED,
        FAILED,
    }

    /**
     * Reset: wipe all of this account's data but keep the login alive. After it
     * returns, restart/recreate the UI so screens re-read the empty stores.
     */
    suspend fun resetData(context: Context): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        wipeEverything(context, uid)
    }

    /**
     * Full delete: reauthenticate, wipe the data, then remove the Firebase Auth
     * account itself.
     *
     * ## Why reauthenticate FIRST
     *
     * `FirebaseUser.delete()` requires a "recent" sign-in — a token that's
     * merely valid (silently auto-refreshed, as it normally is during everyday
     * use) doesn't count. A user almost never deletes their account within
     * minutes of originally signing in, so without this step `delete()` throws
     * [FirebaseAuthRecentLoginRequiredException] nearly every time: the data
     * gets wiped, the Auth record survives, and — this was the actual bug —
     * nothing ever retried the deletion on a later sign-in, so the account
     * looked "deleted" but blocked the same Google account from registering
     * fresh. Reauthenticating up front makes the session fresh on purpose, so
     * `delete()` actually succeeds in the normal case instead of leaving an
     * orphaned Auth record behind.
     *
     * Only Google-signed-in accounts are reauthenticated here (that's this
     * app's primary sign-in path); an email/password account still falls back
     * to [DeleteOutcome.DATA_WIPED_NEEDS_REAUTH] as before.
     */
    suspend fun deleteAccount(context: Context): DeleteOutcome {
        val user = auth.currentUser ?: return DeleteOutcome.FAILED

        if (authRepository.isGoogleAccount()) {
            val reauth = authRepository.reauthenticateWithGoogle(context)
            if (reauth is com.example.kinetixfsl.auth.AuthResult.Error) {
                Log.w(TAG, "reauth before delete failed: ${reauth.message}")
                return DeleteOutcome.REAUTH_CANCELLED
            }
        }

        val uid = user.uid
        runCatching { wipeEverything(context, uid) }
            .onFailure { Log.w(TAG, "data wipe had failures (continuing)", it) }
        return try {
            user.delete().await()
            DeleteOutcome.DELETED
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            // Rare now that we reauthenticate up front (e.g. the fresh session
            // itself expired in the few seconds it took to wipe Firestore data).
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
        deleteUserProfile(uid)
    }

    /**
     * Clears this account's local offline data: the Room/SQLite rows for its uid
     * (progress, activity log, quiz game) plus any leftover legacy
     * SharedPreferences files from before the Room migration.
     */
    private fun clearLocalProgress(context: Context, uid: String) {
        runCatching {
            val db = com.example.kinetixfsl.data.local.KinetixDatabase.get(context)
            db.progressDao().wipe(uid)
            db.activityDao().wipe(uid)
            db.quizDao().wipe(uid)
        }.onFailure { Log.w(TAG, "local Room wipe failed", it) }

        // Legacy prefs (harmless if already migrated/removed).
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

    /**
     * Deletes communities this user created — and crucially, EVERY post inside
     * them (by any author, not just this user), members, then the community doc
     * itself. Without the post sweep, the community's content used to linger in
     * the feed after the owner deleted their account.
     */
    private suspend fun deleteOwnCommunities(uid: String) = runCatching {
        val comms = db.collection(COMMUNITIES).whereEqualTo("creatorId", uid).get().await()
        for (doc in comms.documents) {
            deletePostsInCommunity(doc.id)
            deleteAllIn(doc.reference.collection(MEMBERS))
            runCatching { doc.reference.delete().await() }
        }
    }.onFailure { Log.w(TAG, "own-communities delete failed", it) }.let { }

    /** Deletes every post in a community (any author), tree-first. */
    private suspend fun deletePostsInCommunity(communityId: String) = runCatching {
        val posts = db.collection(POSTS)
            .whereEqualTo(FIELD_COMMUNITY_ID, communityId).get().await()
        for (doc in posts.documents) {
            val ref = doc.reference
            deleteAllIn(ref.collection(COMMENTS))
            deleteAllIn(ref.collection(VOTES))
            deleteAllIn(ref.collection(SHARES))
            runCatching { ref.delete().await() }
        }
    }.onFailure { Log.w(TAG, "community-posts delete failed", it) }.let { }

    /**
     * Removes the user's public profile so a later sign-in starts from a default
     * profile instead of the deleted account's name/photo. Best-effort: if the
     * security rules forbid deleting `users/{uid}`, blank the identifying fields
     * as a fallback so nothing recognisable is left behind.
     */
    private suspend fun deleteUserProfile(uid: String) {
        val ref = db.collection(USERS).document(uid)
        runCatching { deleteAllIn(ref.collection(JOINED)) }
        val deleted = runCatching { ref.delete().await() }.isSuccess
        if (!deleted) {
            runCatching {
                ref.set(
                    mapOf("displayName" to "Anonymous", "avatarUrl" to null),
                    SetOptions.merge(),
                ).await()
            }.onFailure { Log.w(TAG, "profile blank fallback failed", it) }
        }
    }

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
        const val USERS = "users"
        const val JOINED = "joined"
        const val FIELD_COMMUNITY_ID = "communityId"
        const val BATCH = 300
    }
}
