package com.example.kinetixfsl.progress

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * The background cloud-sync worker.
 *
 * WorkManager runs this only once the device actually has a network connection
 * (a `CONNECTED` constraint set at enqueue time), which is exactly the
 * "check connectivity state and sync when online" behaviour the manuscript
 * describes — the OS, not a hand-rolled connectivity listener, decides when to
 * fire it, and retries with backoff if a run fails.
 *
 * Each run reads the CURRENT local state from the Room stores (via
 * [ProgressRepository.buildSyncDocument]) and writes one document to
 * `users/{uid}/progress/current`. Reading at run time means the upload always
 * reflects the latest offline progress, even if several events queued while
 * offline.
 */
class ProgressSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Signed out (or signed out since enqueue): nothing to sync, and we must
        // not stamp one account's data onto another. Treat as done, not failed.
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()

        val document = runCatching {
            ProgressRepository(applicationContext).buildSyncDocument()
        }.getOrElse {
            Log.w(TAG, "building sync document failed", it)
            return Result.retry()
        }

        return runCatching {
            val payload = document + mapOf("updatedAt" to FieldValue.serverTimestamp())
            // Progress lives at users/{uid}/progress/current (the restructure's
            // Phase 3 target — see web/FIRESTORE_RESTRUCTURE.md). The old root
            // progress/{uid} write was removed once the database was confirmed
            // free of any pre-migration installs to carry forward.
            FirebaseFirestore.getInstance()
                .collection(USERS).document(uid)
                .collection(COLLECTION).document(PROGRESS_DOC)
                .set(payload, SetOptions.merge())
                .await()
            Result.success()
        }.getOrElse {
            Log.w(TAG, "cloud push failed; WorkManager will retry", it)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ProgressSyncWorker"
        const val COLLECTION = "progress"
        const val USERS = "users"
        /** Doc id of the single nested progress doc: users/{uid}/progress/current. */
        const val PROGRESS_DOC = "current"
    }
}
