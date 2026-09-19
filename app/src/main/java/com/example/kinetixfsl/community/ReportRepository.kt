package com.example.kinetixfsl.community

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.kinetixfsl.community.model.Comment
import com.example.kinetixfsl.community.model.Post
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Thrown by [ReportRepository.reportPost] when the signed-in user has already
 * reported this post. One report per post per account, so a repeat tap is a
 * normal, user-facing outcome ("Reported already") — not a failure to log.
 */
class AlreadyReportedException : Exception("You already reported this post.")

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

    /**
     * Reports a post — at most once per signed-in user.
     *
     * A `posts/{id}/reporters/{uid}` marker (see web/firestore.rules) is what
     * caps it: the transaction checks that marker first and fails with
     * [AlreadyReportedException] on a repeat report, instead of filing a
     * duplicate and bumping [Post.reportCount] a second time for the same
     * person — the spam path this exists to close.
     *
     * Every photo/clip on the post is denormalised into the report's
     * `contentSnapshot.media`, so an admin can still watch the video or view
     * the images later, even if the post itself gets deleted first.
     */
    suspend fun reportPost(post: Post, reason: String): Result<Unit> = runCatching {
        val me = auth.currentUser ?: error("Not signed in")
        val postRef = db.collection("posts").document(post.id)
        val reporterRef = postRef.collection("reporters").document(me.uid)
        val reportRef = db.collection("reports").document()

        db.runTransaction { txn ->
            if (txn.get(reporterRef).exists()) throw AlreadyReportedException()

            txn.set(reporterRef, mapOf("reportedAt" to FieldValue.serverTimestamp()))
            txn.update(postRef, "reportCount", FieldValue.increment(1))
            txn.set(
                reportRef,
                mapOf(
                    "contentType" to "post",
                    "reportedUserId" to post.authorId,
                    "reportedUserName" to post.authorName,
                    "reporterId" to me.uid,
                    "reporterName" to (me.displayName ?: me.email?.substringBefore('@') ?: "A learner"),
                    "reason" to reason.trim(),
                    "status" to "open",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "postId" to post.id,
                    "communityId" to post.communityId,
                    "communityName" to post.communityName,
                    "contentSnapshot" to mapOf(
                        "title" to post.title,
                        "body" to post.body,
                        // Legacy single-image field, kept for anything still reading it.
                        "imageUrl" to (post.mediaItems.firstOrNull { !it.isVideo }?.feedUrl ?: ""),
                        // Every photo/clip on the post — so the admin can watch the
                        // video and view every image, not just one still.
                        "media" to post.mediaItems.map { m ->
                            mapOf("url" to m.url, "type" to m.type, "thumbUrl" to (m.thumbUrl ?: ""))
                        },
                    ),
                ),
            )
        }.await()
        Unit
    }.onFailure { if (it !is AlreadyReportedException) Log.w(TAG, "report submit failed", it) }

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

/**
 * Submits a post report and shows the outcome as a Toast — success, the
 * "already reported" notice, or a generic failure — so every report dialog
 * (feed, profile, post detail, immersive viewer) gives real feedback instead
 * of always claiming success regardless of what happened.
 */
suspend fun ReportRepository.reportPostShowingResult(context: Context, post: Post, reason: String) {
    val message = reportPost(post, reason).fold(
        onSuccess = { "Thanks — we'll review this post." },
        onFailure = { e -> if (e is AlreadyReportedException) "Reported already" else "Couldn't submit the report. Try again." },
    )
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
