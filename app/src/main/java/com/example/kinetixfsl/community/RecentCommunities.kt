package com.example.kinetixfsl.community

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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
     * The list, newest first. Ordering (and which communities are on it) comes
     * from the user's own `recentCommunities` markers; each row's name and
     * picture come from a LIVE listener on the community document itself, so a
     * rename or a new avatar on a community the user has visited — their own or
     * one they joined — shows here at once, without re-opening that community.
     * The visited markers only seed a fallback for the first frame, since the
     * name/avatar copied into them at visit time go stale.
     *
     * Rows whose community no longer exists are dropped and their marker is
     * deleted: the list is private to the user, so a community deleted by its
     * creator (or gone with the creator's whole account) leaves an orphan
     * nobody else could ever clear.
     */
    fun observe(limit: Long = DRAWER_LIMIT): Flow<List<RecentCommunity>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        // Ordering + first-frame fallback, from the user's own markers.
        var order: List<String> = emptyList()
        val visitedAt = mutableMapOf<String, Timestamp?>()
        val fallback = mutableMapOf<String, RecentCommunity>()
        // Live community docs and their listeners, one per row currently shown.
        val liveDocs = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
        val liveRegs = mutableMapOf<String, ListenerRegistration>()

        fun emitMerged() {
            trySend(
                order.mapNotNull { id ->
                    val doc = liveDocs[id]
                    when {
                        // Confirmed gone — leave it out (and it gets pruned below).
                        doc != null && !doc.exists() -> null
                        // Live doc in hand — use its current name and avatar.
                        doc != null -> RecentCommunity(
                            id = id,
                            name = doc.getString("name").orEmpty()
                                .ifBlank { fallback[id]?.name.orEmpty() },
                            avatarUrl = doc.getString("avatarUrl"),
                            visitedAt = visitedAt[id],
                        )
                        // Not loaded yet — show the stale copy so the row doesn't flicker.
                        else -> fallback[id]?.copy(visitedAt = visitedAt[id])
                    }
                }
            )
        }

        val markerReg = firestore.collection(USERS).document(uid)
            .collection(RECENT)
            .orderBy(FIELD_VISITED_AT, Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                if (snapshot == null) return@addSnapshotListener

                order = snapshot.documents.map { it.id }
                visitedAt.clear()
                fallback.clear()
                snapshot.documents.forEach { d ->
                    visitedAt[d.id] = d.getTimestamp(FIELD_VISITED_AT)
                    fallback[d.id] = RecentCommunity(
                        id = d.id,
                        name = d.getString("name").orEmpty(),
                        avatarUrl = d.getString("avatarUrl"),
                        visitedAt = d.getTimestamp(FIELD_VISITED_AT),
                    )
                }

                val wanted = order.toSet()
                // Drop listeners for communities no longer on the list.
                (liveRegs.keys - wanted).toList().forEach { gone ->
                    liveRegs.remove(gone)?.remove()
                    liveDocs.remove(gone)
                }
                // Attach a listener for each newly-present community.
                (wanted - liveRegs.keys).forEach { id ->
                    liveRegs[id] = firestore.collection(COMMUNITIES).document(id)
                        .addSnapshotListener { cDoc, cErr ->
                            if (cErr != null || cDoc == null) return@addSnapshotListener
                            liveDocs[id] = cDoc
                            if (!cDoc.exists()) {
                                // Prune the orphan marker so it stops coming back.
                                launch {
                                    try {
                                        firestore.collection(USERS).document(uid)
                                            .collection(RECENT).document(id).delete().await()
                                    } catch (_: Exception) { /* best-effort */ }
                                }
                            }
                            emitMerged()
                        }
                }

                emitMerged()
            }

        awaitClose {
            markerReg.remove()
            liveRegs.values.forEach { it.remove() }
            liveRegs.clear()
        }
    }
        // The list rebuilds on every marker/community snapshot (and every
        // pending-write echo). Most of those produce an identical list — drop
        // the duplicates so a collector isn't recomposed for no change.
        .distinctUntilChanged()

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
        const val COMMUNITIES = "communities"
        const val RECENT = "recentCommunities"
        const val FIELD_VISITED_AT = "visitedAt"

        /** How many fit in the drawer before "See all" earns its place. */
        const val DRAWER_LIMIT = 25L

        const val BATCH_LIMIT = 400
    }
}
