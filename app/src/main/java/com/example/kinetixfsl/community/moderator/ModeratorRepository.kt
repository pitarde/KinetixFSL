package com.example.kinetixfsl.community.moderator

import com.example.kinetixfsl.community.model.ModeratorApplication
import com.example.kinetixfsl.community.model.ModeratorApplicationStatus
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore access for the Become-a-Moderator program —
 * `moderatorApplications/{uid}`, one doc per user.
 */
class ModeratorRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {

    /**
     * Writes (or overwrites) the signed-in user's application with a fresh
     * stats snapshot and `status: "pending"` — used both for a first
     * application and for reapplying after a rejection, which is why any
     * previous [ModeratorApplication.reviewedAt]/[ModeratorApplication.rejectionNote]
     * are dropped rather than carried over.
     */
    suspend fun submitApplication(
        displayName: String,
        avatarUrl: String?,
        upvotesTotal: Long,
        followerCount: Long,
        postCount: Long,
        accountAgeDays: Long,
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(Exception("Not signed in."))
        // A full overwrite (not merge) — reapplying after a rejection should
        // drop the old reviewedAt/reviewedBy/rejectionNote, not carry them
        // forward next to a brand-new "pending" status.
        val fields = mapOf(
            "uid" to uid,
            "displayName" to displayName,
            "avatarUrl" to avatarUrl,
            "upvotesTotal" to upvotesTotal,
            "followerCount" to followerCount,
            "postCount" to postCount,
            "accountAgeDays" to accountAgeDays,
            "status" to ModeratorApplicationStatus.PENDING,
            "appliedAt" to Timestamp.now(),
            "reviewedAt" to null,
            "reviewedBy" to null,
            "rejectionNote" to null,
        )
        return try {
            firestore.collection(APPLICATIONS).document(uid).set(fields).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Live view of [uid]'s own application, or null if they've never applied. */
    fun observeMyApplication(uid: String): Flow<ModeratorApplication?> = callbackFlow {
        val reg = firestore.collection(APPLICATIONS).document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(null); return@addSnapshotListener }
                trySend(
                    try {
                        snap?.toObject(ModeratorApplication::class.java)?.copy(uid = uid)
                    } catch (_: Exception) {
                        null
                    }
                )
            }
        awaitClose { reg.remove() }
    }

    private companion object {
        const val APPLICATIONS = "moderatorApplications"
    }
}
