package com.example.kinetixfsl.progress

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Schedules background mirroring of each signed-in user's progress to Firestore
 * (`progress/{uid}`), and restores it on a fresh install.
 *
 * ## The sync mechanism: a WorkManager worker, connectivity-gated
 *
 * A save enqueues a [ProgressSyncWorker] with a `CONNECTED` network constraint
 * and a short initial delay. WorkManager holds the job until the device is
 * online, then runs it (retrying with backoff on failure) — so connectivity
 * handling and retry are the OS's job, and the worker itself just uploads the
 * latest local state. The enqueue is a **unique** job with `REPLACE`, so a burst
 * of events while offline collapses into a single upload when connectivity
 * returns. That keeps writes far under the Firestore free tier.
 */
object ProgressSync {

    private const val TAG = "ProgressSync"

    // A DEDICATED collection, not the public `users` profile doc. Profiles are
    // world-readable (post cards, follower lists); a learner's progress and
    // activity analytics must not be. Security rules make `progress/{uid}`
    // private to that user plus the admin.
    private const val COLLECTION = "progress"
    private const val WORK_NAME = "progress-cloud-sync"
    private const val DEBOUNCE_MS = 2_500L

    // Captured on the first schedule so a later sign-out can cancel the work
    // without the caller needing to thread a Context through.
    @Volatile private var appContext: Context? = null

    private fun db(): FirebaseFirestore = FirebaseFirestore.getInstance()
    private fun uid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    /**
     * Enqueues a connectivity-gated background upload of the current local state.
     * Safe to call on every save: the unique `REPLACE` policy collapses a burst
     * into one run. Skipped when signed out — there is no account to sync to.
     */
    fun scheduleSync(context: Context) {
        if (uid() == null) return
        val app = context.applicationContext.also { appContext = it }

        val request = OneTimeWorkRequestBuilder<ProgressSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInitialDelay(DEBOUNCE_MS, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .build()

        WorkManager.getInstance(app)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Cancel any queued upload. Called on sign-out so account A's pending work
     * never fires against a switched session. No data is lost: A's latest state
     * is in its own local store and re-syncs the next time A signs in.
     */
    fun cancelPending() {
        appContext?.let { WorkManager.getInstance(it).cancelUniqueWork(WORK_NAME) }
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
