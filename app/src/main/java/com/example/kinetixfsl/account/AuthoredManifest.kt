package com.example.kinetixfsl.account

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

/**
 * The deletion manifest — `users/{uid}/authored/{docId}` (see
 * web/FIRESTORE_RESTRUCTURE.md §4.2 Part B).
 *
 * Every time a user writes a document into *someone else's* subtree — a comment
 * or vote on another person's post, a share marker, a follower entry, an
 * outgoing notification — the same transaction also writes one small manifest
 * row here. Account deletion then reads this one collection and clears exactly
 * what it lists, so the cleanup of cross-user data is deterministic and does not
 * depend on collection-group indexes (which fail silently when not deployed —
 * the failure mode called out in firestore.indexes.json).
 *
 * It is best-effort insurance, not the only path: [AccountEraser] still runs its
 * collection-group sweeps afterwards as a fallback, so a manifest row that was
 * never written (an older client, a failed companion write) is still caught.
 *
 * The manifest lives under `users/{uid}`, so it is swept away with the rest of
 * the profile once it has been consumed.
 */
object AuthoredManifest {

    const val COLLECTION = "authored"

    // Manifest types — mirror the `type` column in the plan.
    const val TYPE_COMMENT = "comment"
    const val TYPE_VOTE = "vote"
    const val TYPE_COMMENT_VOTE = "commentVote"
    const val TYPE_SHARE = "share"
    const val TYPE_FOLLOWER = "follower"
    const val TYPE_NOTIFICATION = "notification"

    /**
     * A stable manifest doc id derived from the target document's full path.
     *
     * Deterministic on purpose: writing the same cross-user document twice
     * overwrites the one manifest row instead of duplicating it, and undoing the
     * action (unfollow, retracting a vote) can delete the row by the same key.
     * `/` is illegal in a Firestore document id; `~` is allowed, and none of our
     * paths contain it, so the mapping is unambiguous.
     */
    fun idFor(path: String): String = path.replace('/', '~')

    /** The manifest row for [targetPath], under [ownerUid]'s profile. */
    fun refFor(
        db: FirebaseFirestore,
        ownerUid: String,
        targetPath: String,
    ): DocumentReference =
        db.collection("users").document(ownerUid)
            .collection(COLLECTION).document(idFor(targetPath))

    /**
     * The manifest payload. [counter] names a single field on [parentPath] to
     * repair after the row is deleted, or null when the parent is recounted from
     * its surviving children instead (votes) or has no counter (notifications).
     */
    fun entry(
        path: String,
        type: String,
        parentPath: String?,
        counter: String?,
    ): Map<String, Any?> = hashMapOf(
        "path" to path,
        "type" to type,
        "parentPath" to parentPath,
        "counter" to counter,
        "createdAt" to Timestamp.now(),
    )
}
