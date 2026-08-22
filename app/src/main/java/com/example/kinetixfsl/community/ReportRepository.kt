package com.example.kinetixfsl.community

import android.util.Log
import com.example.kinetixfsl.community.model.Comment
import com.example.kinetixfsl.community.model.Post
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Files community reports into the top-level `reports` collection, which the
 * admin web console's Reports & Moderation queue reads.
 *
 * The reported content is denormalised into the report document (a
 * `contentSnapshot`) on purpose: by the time an admin reviews the queue the
 * offending post or comment may already have been deleted, and the moderator
 * still needs to see what was reported to make a decision.
 *
 * The security rules only let a signed-in user *create* a report attributed to
 * themselves and always with `status == "open"`; reading and resolving the
 * queue is admin-only. See web/firestore.rules → match /reports.
 */
class ReportRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {

    /** Report a post. [reason] is the reporter's free-text note. */
    suspend fun reportPost(post: Post, reason: String): Result<Unit> = submit(
        contentType = "post",
        reportedUserId = post.authorId,
        reportedUserName = post.authorName,
        reason = reason,
        extra = mapOf(
            "postId" to post.id,
            "communityId" to post.communityId,
            "communityName" to post.communityName,
            "contentSnapshot" to mapOf(
                "title" to post.title,
                "body" to post.body,
                "imageUrl" to (post.mediaItems.firstOrNull()?.feedUrl ?: ""),
            ),
        ),
    )

    /** Report a single comment on a post. */
    suspend fun reportComment(postId: String, comment: Comment, reason: String): Result<Unit> = submit(
        contentType = "comment",
        reportedUserId = comment.authorId,
        reportedUserName = comment.authorName,
        reason = reason,
        extra = mapOf(
            "postId" to postId,
            "commentId" to comment.id,
            "contentSnapshot" to mapOf(
                "body" to comment.body,
                "imageUrl" to (comment.imageUrl ?: ""),
            ),
        ),
    )

    /** Report a user from a direct-message conversation. */
    suspend fun reportUser(
        reportedUserId: String,
        reportedUserName: String,
        conversationId: String,
        reason: String,
    ): Result<Unit> = submit(
        contentType = "chat",
        reportedUserId = reportedUserId,
        reportedUserName = reportedUserName,
        reason = reason,
        extra = mapOf("conversationId" to conversationId),
    )

    private suspend fun submit(
        contentType: String,
        reportedUserId: String,
        reportedUserName: String,
        reason: String,
        extra: Map<String, Any?>,
    ): Result<Unit> = runCatching {
        val me = auth.currentUser ?: error("Not signed in")
        val base = mapOf(
            "contentType" to contentType,
            "reportedUserId" to reportedUserId,
            "reportedUserName" to reportedUserName,
            "reporterId" to me.uid,
            "reporterName" to (me.displayName ?: me.email?.substringBefore('@') ?: "A learner"),
            "reason" to reason.trim(),
            "status" to "open",
            "createdAt" to FieldValue.serverTimestamp(),
        )
        db.collection("reports").add(base + extra).await()
        Unit
    }.onFailure { Log.w(TAG, "report submit failed", it) }

    private companion object {
        const val TAG = "ReportRepository"
    }
}
