package com.example.kinetixfsl.community

import com.example.kinetixfsl.community.model.Comment
import com.example.kinetixfsl.community.model.Post
import com.example.kinetixfsl.community.model.PostMedia
import com.example.kinetixfsl.community.model.UserComment
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
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
                trySend(snapshot.documents.mapNotNull { it.toPostOrNull() })
            }
        awaitClose { registration.remove() }
    }

    // -------------------------------------------------------------------------
    // Profile
    // -------------------------------------------------------------------------

    /** Every post by [uid], newest first. Backs the profile's Posts tab. */
    fun postsByAuthor(uid: String): Flow<List<Post>> = callbackFlow {
        val reg = firestore.collection(POSTS)
            .whereEqualTo("authorId", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener
                // Sorted here rather than with orderBy so this needs no
                // composite index — a single user's posts are a small set.
                trySend(
                    snap.documents
                        .mapNotNull { it.toPostOrNull() }
                        .sortedByDescending { it.createdAt }
                )
            }
        awaitClose { reg.remove() }
    }

    /**
     * Every comment by [uid] across all posts, newest first, each paired with
     * the post it belongs to so the profile can show the post's title.
     *
     * Uses a collection-group query, which needs a one-off index in the
     * Firebase console — Firestore's error message links straight to it.
     */
    fun commentsByAuthor(uid: String): Flow<List<UserComment>> = callbackFlow {
        val reg = firestore.collectionGroup(COMMENTS)
            .whereEqualTo("authorId", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener

                val items = snap.documents.mapNotNull { doc ->
                    try {
                        val comment = doc.toObject(Comment::class.java)
                            ?.copy(id = doc.id) ?: return@mapNotNull null
                        // posts/{postId}/comments/{commentId} — hop up two
                        // levels to recover which post this belongs to.
                        val postId = doc.reference.parent.parent?.id
                            ?: return@mapNotNull null
                        UserComment(comment = comment, postId = postId)
                    } catch (_: Exception) {
                        null
                    }
                }.sortedByDescending { it.comment.createdAt }

                trySend(items)
            }
        awaitClose { reg.remove() }
    }

    /** Titles for the posts a user commented on, keyed by post id. */
    suspend fun postTitles(postIds: Collection<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        postIds.distinct().forEach { id ->
            try {
                val doc = firestore.collection(POSTS).document(id).get().await()
                doc.getString("title")?.let { result[id] = it }
            } catch (_: Exception) {
                // A missing post just shows without a title.
            }
        }
        return result
    }

    /** Removes a post. Only the author is allowed to, enforced by rules. */
    suspend fun deletePost(postId: String): Result<Unit> = try {
        firestore.collection(POSTS).document(postId).delete().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Edits a post's text. Media is left alone — changing attachments would
     * mean re-running the whole upload pipeline, so the editor covers the
     * title, body and link only.
     */
    suspend fun updatePost(
        postId: String,
        title: String,
        body: String,
        linkUrl: String?,
        media: List<PostMedia>,
    ): Result<Unit> = try {
        // Legacy single-media fields are rewritten too, so the share page and
        // any older client stay consistent with the new attachment list.
        firestore.collection(POSTS).document(postId).update(
            mapOf(
                "title" to title.trim(),
                "body" to body.trim(),
                "linkUrl" to linkUrl?.trim()?.takeIf { it.isNotBlank() },
                "media" to media.map {
                    hashMapOf("url" to it.url, "type" to it.type, "thumbUrl" to it.thumbUrl)
                },
                "imageUrl" to media.firstOrNull { !it.isVideo }?.url,
                "videoUrl" to media.firstOrNull { it.isVideo }?.url,
                "editedAt" to Timestamp.now(),
            )
        ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // -------------------------------------------------------------------------
    // Create post
    // -------------------------------------------------------------------------

    suspend fun createPost(
        title: String,
        body: String,
        linkUrl: String? = null,
        media: List<PostMedia> = emptyList(),
        previewUrl: String? = null,
        previewBlur: String? = null,
    ): Result<String> {
        val user = auth.currentUser ?: return Result.failure(Exception("Not signed in."))

        // The legacy single-media fields still get the first image and the
        // first video, so the share page and any older build keep rendering
        // something sensible for a multi-media post.
        val imageUrl = media.firstOrNull { !it.isVideo }?.url
        val videoUrl = media.firstOrNull { it.isVideo }?.url

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
            "media" to media.map {
                hashMapOf("url" to it.url, "type" to it.type, "thumbUrl" to it.thumbUrl)
            },
            "previewUrl" to previewUrl,
            "previewBlur" to previewBlur,
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
                trySend(
                    snap.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(Comment::class.java)?.copy(id = doc.id)
                        } catch (_: Exception) {
                            null
                        }
                    }
                )
            }
        awaitClose { reg.remove() }
    }

    /**
     * Adds a comment. [imageUrl] is the already-uploaded R2 URL of the single
     * optional image the commenter attached — null when the comment is text only.
     */
    /**
     * Adds a comment, or a reply when [parentId] is the id of the top-level
     * comment being replied to. Replies go in the same subcollection, so the
     * existing listener picks them up without a second query.
     */
    suspend fun addComment(
        postId: String,
        body: String,
        imageUrl: String? = null,
        parentId: String? = null,
    ): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("Not signed in."))
        val data = hashMapOf(
            "authorId" to user.uid,
            "authorName" to (user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore('@') ?: "Anonymous"),
            "authorAvatarUrl" to (user.photoUrl?.toString()),
            "body" to body.trim(),
            "imageUrl" to imageUrl,
            "parentId" to parentId,
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

    /**
     * Records that the signed-in user shared this post.
     *
     * The count is per account, not per tap: a `shares/{uid}` marker document
     * makes the increment idempotent, so sharing the same post ten times still
     * reads as one share — the way every other social platform counts it.
     */
    suspend fun sharePost(postId: String) {
        val uid = auth.currentUser?.uid ?: return
        val postRef = firestore.collection(POSTS).document(postId)
        val shareRef = postRef.collection(SHARES).document(uid)
        try {
            firestore.runTransaction { tx ->
                val existing = tx.get(shareRef)
                if (!existing.exists()) {
                    tx.set(shareRef, hashMapOf("sharedAt" to Timestamp.now()))
                    tx.update(postRef, "shareCount", FieldValue.increment(1))
                }
            }.await()
        } catch (_: Exception) { /* best-effort */ }
    }

    /** Loads a single post by id — used when opening a shared link. */
    suspend fun getPost(postId: String): Result<Post> {
        return try {
            val doc = firestore.collection(POSTS).document(postId).get().await()
            val post = doc.toPostOrNull()
            if (post == null) {
                Result.failure(Exception("This post is no longer available."))
            } else {
                Result.success(post)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Live updates for a single post, so a linked post's counts stay current. */
    fun observePost(postId: String): Flow<Post?> = callbackFlow {
        val reg = firestore.collection(POSTS).document(postId)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toPostOrNull())
            }
        awaitClose { reg.remove() }
    }

    // -------------------------------------------------------------------------

    /**
     * Deserializes one post document, returning null instead of throwing.
     *
     * This is load-bearing: a snapshot listener maps every document in the
     * result, so a single document that fails to deserialize used to throw out
     * of the whole callback. The flow then never emitted again and the feed
     * froze on stale data — which is exactly what happened when posts started
     * carrying the new `media` array. One malformed document must only cost
     * that document.
     */
    private fun DocumentSnapshot.toPostOrNull(): Post? = try {
        toObject(Post::class.java)?.copy(id = id)
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val POSTS = "posts"
        const val VOTES = "votes"
        const val COMMENTS = "comments"
        const val SHARES = "shares"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_SCORE = "score"
        const val FEED_PAGE_SIZE = 50L
    }
}