package com.example.kinetixfsl.community

import com.example.kinetixfsl.community.model.Post
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The single point of contact with Firestore for community data. Any screen
 * or ViewModel that needs posts (or, later, comments/votes) goes through here.
 *
 * `feedPosts()` is a live Flow — Firestore pushes updates as soon as anyone
 * (including this same app on another device) adds, edits, or deletes a post.
 */
class CommunityRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    /**
     * Newest posts first. Emits every time the underlying collection changes;
     * cancelling collection removes the Firestore listener.
     */
    fun feedPosts(limit: Long = FEED_PAGE_SIZE): Flow<List<Post>> = callbackFlow {
        val registration = firestore.collection(POSTS)
            .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Fail the Flow so the ViewModel can render an error state.
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val posts = snapshot.documents.mapNotNull { doc ->
                    // toObject can return null if a required field is missing.
                    doc.toObject(Post::class.java)?.copy(id = doc.id)
                }
                trySend(posts)
            }

        // Detach the Firestore listener when the collector stops (screen leaves).
        awaitClose { registration.remove() }
    }

    private companion object {
        const val POSTS = "posts"
        const val FIELD_CREATED_AT = "createdAt"
        const val FEED_PAGE_SIZE = 50L
    }
}