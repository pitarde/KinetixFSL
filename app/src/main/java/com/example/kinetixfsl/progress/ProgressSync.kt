package com.example.kinetixfsl.progress

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mirrors each signed-in user's progress + activity to Firestore, one document
 * per account at `users/{uid}`.
 *
 * ## Why there is no hand-written "sync engine"
 *
 * Firestore's Android SDK has **offline persistence on by default**: a write
 * lands in a local cache immediately and returns; when the phone next has data
 * or Wi-Fi, the SDK flushes queued writes to the server automatically, and
 * reads fall back to that cache while offline. So the *local database* and the
 * *online sync* are both the SDK's job — this object only decides WHAT to write
 * and WHEN, and reads the cloud copy back to restore a fresh install / new
 * device.
 *
 * ## Keeping Firestore reads/writes cheap
 *
 * Everything for one user is a single document, and pushes are **debounced**
 * (a burst of XP events collapses into one write). So a practice session is
 * ~1 write, and the admin reading a user is ~1 read — nowhere near the free
 * tier's 20k writes / 50k reads per day.
 */
object ProgressSync {

    private const val TAG = "ProgressSync"

    // A DEDICATED collection, not the public `users` profile doc. Profiles are
    // world-readable (post cards, follower lists); a learner's progress and
    // activity analytics must not be. Security rules make `progress/{uid}`
    // private to that user plus the admin.
    private const val COLLECTION = "progress"
    private const val DEBOUNCE_MS = 2_500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null

    private fun db(): FirebaseFirestore = FirebaseFirestore.getInstance()
    private fun uid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    /**
     * Queues a push of the whole per-user document. Safe to call on every save —
     * repeated calls within [DEBOUNCE_MS] collapse into a single Firestore write,
     * always using the most recent document.
     *
     * A signed-out ("guest") session is skipped: there is no account to sync to.
     */
    fun schedulePush(document: Map<String, Any?>) {
        // Capture the account NOW. If it changes before the debounce fires
        // (sign-out / switch), we drop the write rather than stamp account A's
        // data onto account B's document.
        val id = uid() ?: return
        pending?.cancel()
        pending = scope.launch {
            delay(DEBOUNCE_MS)
            if (uid() != id) return@launch
            val doc = document + mapOf("updatedAt" to FieldValue.serverTimestamp())
            runCatching {
                // set() updates the offline cache instantly and syncs when online.
                db().collection(COLLECTION).document(id).set(doc, SetOptions.merge())
            }.onFailure { Log.w(TAG, "cloud push scheduling failed", it) }
        }
    }

    /**
     * Drop any queued push. Call on sign-out BEFORE Firebase clears the uid, so
     * account A's pending write never fires against a signed-out / switched
     * session. No data is lost: account A's latest state is still in its own
     * local store and re-pushes the next time that account signs in.
     */
    fun cancelPending() {
        pending?.cancel()
        pending = null
    }

    /**
     * If there is NO local progress yet (fresh install or a new device) but the
     * signed-in account has a cloud copy, restore it into the local stores.
     * Returns true if a restore happened. Never overwrites existing local data.
     *
     * Call once at app start, after auth is known.
     */
    suspend fun restoreFromCloudIfLocalEmpty(context: Context): Boolean {
        val id = uid() ?: return false

        val progressStore = ProgressStore(context)
        val local = progressStore.load()
        val hasLocal = local.learnedSignIds.isNotEmpty() ||
            local.currentStreak > 0 ||
            local.unlockedAchievements.isNotEmpty()
        if (hasLocal) return false

        val data = withContext(Dispatchers.IO) {
            runCatching {
                Tasks.await(db().collection(COLLECTION).document(id).get()).data
            }.getOrNull()
        } ?: return false

        val progressJson = data["progressJson"] as? String
        if (progressJson.isNullOrBlank()) return false
        progressStore.saveRawJson(progressJson)

        (data["activityJson"] as? String)?.takeIf { it.isNotBlank() }?.let {
            ActivityLogStore(context).saveRawJson(it)
        }
        (data["quizJson"] as? String)?.takeIf { it.isNotBlank() }?.let {
            com.example.kinetixfsl.game.data.QuizStore(context).saveRawJson(it)
        }

        Log.d(TAG, "Restored progress from cloud for $id")
        return true
    }
}
