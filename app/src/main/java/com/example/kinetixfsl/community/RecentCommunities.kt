package com.example.kinetixfsl.community

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** One row in the drawer's Recently Visited list. */
data class RecentCommunity(
    val id: String = "",
    val name: String = "",
    val avatarUrl: String? = null,
    val visitedAt: Timestamp? = null,
)

/**
 * The communities this user has opened, joined or created, most recent first —
 * the drawer's Recently Visited section.
 *
 * Stored per-user under `users/{uid}/recentCommunities/{communityId}`, keyed by
 * the community id rather than appended to a log. That's what makes revisiting
 * a community move it to the top instead of adding a duplicate entry, with no
 * de-duplication pass on read.
 *
 * Private to the user: this is browsing history, and nobody else has any
 * business reading it.
 */
class RecentCommunitiesRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {

    /**
     * Records a visit. Called when a community screen opens, and when one is
     * joined or created — all three are the same fact from the list's point of
     * view: "this community is one you've dealt with".
     *
     * Name and avatar are copied in so the drawer renders without a read per
     * row. They go stale if a community is renamed, which is the accepted cost
     * of a history list; opening it again refreshes the entry.
     */
    suspend fun record(communityId: String, name: String, avatarUrl: String?) {
        val uid = auth.currentUser?.uid ?: return
        if (communityId.isBlank()) return
        try {
            firestore.collection(USERS).document(uid)
                .collection(RECENT).document(communityId)
                .set(
                    mapOf(
                        "name" to name,
                        "avatarUrl" to avatarUrl,
                        "visitedAt" to Timestamp.now(),
                    ),
                    SetOptions.merge(),
                )
                .await()
        } catch (_: Exception) { /* best-effort — history is never worth an error */ }
    }

    /**
     * The list, newest first. Ordered on a single field, so Firestore's
     * automatic single-field index covers it and no composite index is needed.
     */
    fun observe(limit: Long = DRAWER_LIMIT): Flow<List<RecentCommunity>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = firestore.collection(USERS).document(uid)
            .collection(RECENT)
            .orderBy(FIELD_VISITED_AT, Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                if (snapshot == null) return@addSnapshotListener
                trySend(
                    snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(RecentCommunity::class.java)?.copy(id = doc.id)
                        } catch (_: Exception) {
                            null
                        }
                    }
                )
            }
        awaitClose { registration.remove() }
    }

    /** Removes one entry — the ✕ on a row. */
    suspend fun remove(communityId: String) {
        val uid = auth.currentUser?.uid ?: return
        try {
            firestore.collection(USERS).document(uid)
                .collection(RECENT).document(communityId)
                .delete().await()
        } catch (_: Exception) { /* best-effort */ }
    }

    /** Empties the list — "Clear all". */
    suspend fun clearAll() {
        val uid = auth.currentUser?.uid ?: return
        try {
            val all = firestore.collection(USERS).document(uid)
                .collection(RECENT).get().await()
            all.documents.chunked(BATCH_LIMIT).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        } catch (_: Exception) { /* best-effort */ }
    }

    private companion object {
        const val USERS = "users"
        const val RECENT = "recentCommunities"
        const val FIELD_VISITED_AT = "visitedAt"

        /** How many fit in the drawer before "See all" earns its place. */
        const val DRAWER_LIMIT = 25L

        const val BATCH_LIMIT = 400
    }
}
