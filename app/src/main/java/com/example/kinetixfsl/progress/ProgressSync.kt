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
    /** Doc id of the single nested progress doc: users/{uid}/progress/current. */
    private const val PROGRESS_DOC = "current"
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

        // Progress lives at users/{uid}/progress/current (the restructure's
        // Phase 3 target). The old-root fallback read was removed once the
        // database was confirmed free of any pre-migration installs to carry
        // forward — see web/FIRESTORE_RESTRUCTURE.md.
        val data = withContext(Dispatchers.IO) {
            runCatching {
                Tasks.await(
                    db().collection("users").document(id)
                        .collection(COLLECTION).document(PROGRESS_DOC).get(),
                )
            }.getOrNull()?.data
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

    /**
     * Honours an admin "delete account data" action on THIS device.
     *
     * The admin console wipes the account's cloud data and stamps
     * `accountStatus/{uid}.wipedAt`. But the learner's own phone still holds a
     * local Room copy that would otherwise re-sync the deleted progress straight
     * back. On each sign-in we compare that timestamp to the last one we've
     * applied locally; if the account was wiped more recently, we clear the
     * on-device stores once so the reset actually holds here too.
     *
     * Must run BEFORE [restoreFromCloudIfLocalEmpty] so the restore sees empty
     * local data (and the cloud is empty too, so nothing comes back).
     */
    suspend fun applyRemoteWipeIfNeeded(context: Context): Boolean {
        val id = uid() ?: return false
        // Account status is migrating to users/{uid}/status/moderation; read the
        // new nested path first, then the old accountStatus root (admin
        // dual-writes both during Phase 2).
        val wipedAt = withContext(Dispatchers.IO) {
            runCatching {
                val newSnap = Tasks.await(
                    db().collection("users").document(id)
                        .collection("status").document("moderation").get(),
                )
                if (newSnap.exists()) newSnap
                else Tasks.await(db().collection("accountStatus").document(id).get())
            }.getOrNull()?.getTimestamp("wipedAt")
        } ?: return false

        val wipedMs = wipedAt.toDate().time
        val prefs = context.applicationContext
            .getSharedPreferences("kinetix_wipe", Context.MODE_PRIVATE)
        if (wipedMs <= prefs.getLong("handled_$id", 0L)) return false

        runCatching {
            val local = com.example.kinetixfsl.data.local.KinetixDatabase.get(context)
            local.progressDao().wipe(id)
            local.activityDao().wipe(id)
            local.quizDao().wipe(id)
        }.onFailure { Log.w(TAG, "local wipe (remote-triggered) failed", it) }

        prefs.edit().putLong("handled_$id", wipedMs).apply()
        Log.d(TAG, "Applied admin data wipe locally for $id")
        return true
    }
}
