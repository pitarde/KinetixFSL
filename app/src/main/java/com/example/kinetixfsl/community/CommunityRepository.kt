package com.example.kinetixfsl.community

import com.example.kinetixfsl.community.model.Comment
import com.example.kinetixfsl.community.model.Post
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * The single point of contact with Firestore for community data.
 *
 * The home feed fetches posts ordered by createdAt (to get recent ones), then
 * the ViewModel shuffles them so every user and community gets equal visibility.
 * Score-based sorting will be implemented inside individual communities later.
 */
class CommunityRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    // -------------------------------------------------------------------------
    // Feed
    // -------------------------------------------------------------------------

    /**
     * Fetches recent posts ordered by creation time (newest first).
     * The ViewModel is responsible for shuffling these into a random display order.
     * Single-field ordering — no composite Firestore index needed.
     */
    fun feedPosts(limit: Long = FEED_PAGE_SIZE): Flow<List<Post>> = callbackFlow {
        val registration = firestore.collection(POSTS)
            .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot == null) return@addSnapshotListener
                val posts = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Post::class.java)?.copy(id = doc.id)
                }
                trySend(posts)
            }
        awaitClose { registration.remove() }
    }

    // -------------------------------------------------------------------------
    // Create post
    // -------------------------------------------------------------------------

    suspend fun createPost(
        title: String,
        body: String,
        linkUrl: String? = null,
        imageUrl: String? = null,
        videoUrl: String? = null,
    ): Result<String> {
        val user = auth.currentUser ?: return Result.failure(Exception("Not signed in."))
        val data = hashMapOf(
            "authorId" to user.uid,
            "authorName" to (user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore('@') ?: "Anonymous"),
            "authorAvatarUrl" to (user.photoUrl?.toString()),
            "title" to title.trim(),
            "body" to body.trim(),
            "linkUrl" to linkUrl?.trim()?.takeIf { it.isNotBlank() },
            "imageUrl" to imageUrl,
            "videoUrl" to videoUrl,
            "upvoteCount" to 0L,
            "downvoteCount" to 0L,
            "commentCount" to 0L,
            "shareCount" to 0L,
            "viewCount" to 0L,
            "score" to 0L,
            "createdAt" to Timestamp.now(),
        )
        return try {
            val ref = firestore.collection(POSTS).add(data).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // Voting
    // -------------------------------------------------------------------------

    suspend fun getUserVote(postId: String): String? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val doc = firestore.collection(POSTS).document(postId)
                .collection(VOTES).document(uid).get().await()
            doc.getString("direction")
        } catch (_: Exception) { null }
    }

    suspend fun vote(postId: String, direction: String): String? {
        val uid = auth.currentUser?.uid ?: return null
        val postRef = firestore.collection(POSTS).document(postId)
        val voteRef = postRef.collection(VOTES).document(uid)

        return try {
            firestore.runTransaction { tx ->
                val voteDoc = tx.get(voteRef)
                val existing = voteDoc.getString("direction")

                when {
                    existing == direction -> {
                        tx.delete(voteRef)
                        val countField = if (direction == "up") "upvoteCount" else "downvoteCount"
                        val scoreDelta = if (direction == "up") -1L else 1L
                        tx.update(postRef, countField, FieldValue.increment(-1))
                        tx.update(postRef, FIELD_SCORE, FieldValue.increment(scoreDelta))
                        null
                    }
                    existing != null -> {
                        tx.set(voteRef, hashMapOf("direction" to direction))
                        val incField = if (direction == "up") "upvoteCount" else "downvoteCount"
                        val decField = if (direction == "up") "downvoteCount" else "upvoteCount"
                        val scoreDelta = if (direction == "up") 2L else -2L
                        tx.update(postRef, incField, FieldValue.increment(1))
                        tx.update(postRef, decField, FieldValue.increment(-1))
                        tx.update(postRef, FIELD_SCORE, FieldValue.increment(scoreDelta))
                        direction
                    }
                    else -> {
                        tx.set(voteRef, hashMapOf("direction" to direction))
                        val countField = if (direction == "up") "upvoteCount" else "downvoteCount"
                        val scoreDelta = if (direction == "up") 1L else -1L
                        tx.update(postRef, countField, FieldValue.increment(1))
                        tx.update(postRef, FIELD_SCORE, FieldValue.increment(scoreDelta))
                        direction
                    }
                }
            }.await()
        } catch (_: Exception) { null }
    }

    // -------------------------------------------------------------------------
    // Comments
    // -------------------------------------------------------------------------

    fun commentsForPost(postId: String): Flow<List<Comment>> = callbackFlow {
        val reg = firestore.collection(POSTS).document(postId)
            .collection(COMMENTS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener
                val comments = snap.documents.mapNotNull { doc ->
                    doc.toObject(Comment::class.java)?.copy(id = doc.id)
                }
                trySend(comments)
            }
        awaitClose { reg.remove() }
    }

    suspend fun addComment(postId: String, body: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("Not signed in."))
        val data = hashMapOf(
            "authorId" to user.uid,
            "authorName" to (user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore('@') ?: "Anonymous"),
            "body" to body.trim(),
            "createdAt" to Timestamp.now(),
        )
        return try {
            val postRef = firestore.collection(POSTS).document(postId)
            firestore.runTransaction { tx ->
                tx.update(postRef, "commentCount", FieldValue.increment(1))
                tx.set(postRef.collection(COMMENTS).document(), data)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // Share
    // -------------------------------------------------------------------------

    suspend fun incrementShareCount(postId: String) {
        try {
            firestore.collection(POSTS).document(postId)
                .update("shareCount", FieldValue.increment(1))
                .await()
        } catch (_: Exception) { /* best-effort */ }
    }

    // -------------------------------------------------------------------------

    private companion object {
        const val POSTS = "posts"
        const val VOTES = "votes"
        const val COMMENTS = "comments"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_SCORE = "score"
        const val FEED_PAGE_SIZE = 50L
    }
}