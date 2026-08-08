package com.example.kinetixfsl.community

import com.example.kinetixfsl.community.model.Comment
import com.example.kinetixfsl.community.model.Post
import com.example.kinetixfsl.community.model.PostMedia
import com.example.kinetixfsl.community.model.FollowUser
import com.example.kinetixfsl.community.model.UserComment
import com.example.kinetixfsl.community.model.UserProfile
import com.example.kinetixfsl.community.model.storageKeyOf
import com.example.kinetixfsl.community.inbox.MessagesRepository
import com.example.kinetixfsl.community.inbox.NotificationRepository
import com.example.kinetixfsl.community.inbox.model.NotificationType
import com.example.kinetixfsl.community.upload.R2MediaUploader
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
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
    /**
     * Writes the Inbox rows that community actions produce — a follow, an
     * upvote, a comment, a mention, a community post.
     *
     * Injected rather than reached for statically so a test can pass a
     * no-op, and so it stays obvious from the constructor that writing to
     * this repository also writes into other people's notification trees.
     */
    private val notifications: NotificationRepository = NotificationRepository(),
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

    /**
     * Every post published to one community. No `orderBy` — an equality filter
     * alone needs no composite index (same reasoning as [postsByAuthor]); the
     * feed ViewModel ranks these by score so the order updates as votes change.
     */
    /** One-shot list of a community's posts — used when deleting the community. */
    suspend fun postsInCommunity(communityId: String): List<Post> = try {
        firestore.collection(POSTS)
            .whereEqualTo(FIELD_COMMUNITY_ID, communityId)
            .get().await()
            .documents.mapNotNull { it.toPostOrNull() }
    } catch (_: Exception) {
        emptyList()
    }

    fun communityPosts(communityId: String): Flow<List<Post>> = callbackFlow {
        val registration = firestore.collection(POSTS)
            .whereEqualTo(FIELD_COMMUNITY_ID, communityId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot == null) return@addSnapshotListener
                trySend(snapshot.documents.mapNotNull { it.toPostOrNull() })
            }
        awaitClose { registration.remove() }
    }

    // -------------------------------------------------------------------------
    // Following
    // -------------------------------------------------------------------------

    /**
     * Writes the signed-in user's name and photo to `users/{uid}`.
     *
     * Follower lists need somewhere to read a user's details from, and posts
     * alone aren't enough — someone can be followed without having posted.
     * Merged so it never clobbers the counters.
     */
    suspend fun ensureUserProfile() {
        val user = auth.currentUser ?: return
        try {
            val fields = mutableMapOf<String, Any?>(
                "uid" to user.uid,
                "displayName" to (user.displayName?.takeIf { it.isNotBlank() }
                    ?: user.email?.substringBefore('@') ?: "Anonymous"),
                "avatarUrl" to user.photoUrl?.toString(),
                // Mirrored from Auth because other users can't read our Auth
                // metadata — this is the only source for "Account Age" when
                // somebody else views this profile.
                "lastActiveAt" to Timestamp.now(),
            )
            user.metadata?.creationTimestamp?.let {
                fields["createdAt"] = Timestamp(java.util.Date(it))
            }

            firestore.collection(USERS).document(user.uid)
                .set(fields, SetOptions.merge())
                .await()
        } catch (_: Exception) { /* best-effort */ }
    }

    /**
     * Stamps the signed-in user as active right now. Called when the app comes
     * to the foreground so other people's profile views show a current
     * "Active now / 5min ago" state.
     */
    suspend fun touchLastActive() {
        val uid = auth.currentUser?.uid ?: return
        try {
            firestore.collection(USERS).document(uid)
                .set(mapOf("lastActiveAt" to Timestamp.now()), SetOptions.merge())
                .await()
        } catch (_: Exception) { /* best-effort */ }
    }

    /**
     * Saves the signed-in user's profile picture and/or cover banner to
     * `users/{uid}`. Only the fields passed are written, merged so counters and
     * other details are never clobbered. Mirrors [updateCommunityImages] for the
     * community; the profile Edit sheet uses it.
     */
    suspend fun updateUserImages(
        avatarUrl: String? = null,
        bannerUrl: String? = null,
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("You're not signed in."))
        val fields = mutableMapOf<String, Any?>()
        avatarUrl?.let { fields["avatarUrl"] = it }
        bannerUrl?.let { fields["bannerUrl"] = it }
        if (fields.isEmpty()) return Result.success(Unit)
        return try {
            firestore.collection(USERS).document(uid)
                .set(fields, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Renames the signed-in user, in both Auth and `users/{uid}` — Auth is what
     * new posts and comments stamp their author name from, so both have to move
     * together or old and new content would show two different names.
     */
    suspend fun updateDisplayName(name: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("You're not signed in."))
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(Exception("Name can't be empty."))
        return try {
            user.updateProfile(
                com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(trimmed)
                    .build(),
            ).await()
            firestore.collection(USERS).document(user.uid)
                .set(mapOf("displayName" to trimmed), SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Rewrites the `authorName` denormalized onto every post and comment [uid]
     * has ever made, so a rename doesn't leave old content showing the old name.
     * Best-effort and unbounded — for the account sizes this app deals with, a
     * rename is rare enough that this is worth doing eagerly rather than only
     * lazily resolving names from the live profile everywhere they're shown.
     */
    suspend fun propagateAuthorName(uid: String, newName: String) {
        try {
            val posts = firestore.collection(POSTS).whereEqualTo("authorId", uid).get().await()
            writeFieldInBatches(posts.documents.map { it.reference }, "authorName", newName)
        } catch (_: Exception) { /* best-effort */ }

        try {
            val comments = firestore.collectionGroup(COMMENTS).whereEqualTo("authorId", uid).get().await()
            writeFieldInBatches(comments.documents.map { it.reference }, "authorName", newName)
        } catch (_: Exception) { /* best-effort */ }

        // The Inbox denormalises names too, in two more places: every chat
        // thread carries both participants' details so the list renders in one
        // query, and every notification carries its sender's. Neither updates
        // itself, so a rename that stopped at posts and comments left the other
        // person's inbox showing the old name indefinitely.
        val avatarUrl = try {
            firestore.collection(USERS).document(uid).get().await().getString("avatarUrl")
        } catch (_: Exception) {
            null
        }

        MessagesRepository().propagateProfile(newName, avatarUrl)
        notifications.propagateSenderName(newName, avatarUrl)
    }

    /**
     * Merges a single field across many documents, chunked well under
     * Firestore's 500-write batch limit.
     */
    private suspend fun writeFieldInBatches(refs: List<DocumentReference>, field: String, value: Any?) {
        refs.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { ref -> batch.set(ref, mapOf(field to value), SetOptions.merge()) }
            batch.commit().await()
        }
    }

    /** Ids the signed-in user follows. Drives every Follow button's state. */
    fun observeFollowing(): Flow<Set<String>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptySet())
            awaitClose { }
            return@callbackFlow
        }

        val reg = firestore.collection(USERS).document(uid)
            .collection(FOLLOWING)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptySet()); return@addSnapshotListener }
                trySend(snap?.documents?.map { it.id }?.toSet() ?: emptySet())
            }
        awaitClose { reg.remove() }
    }

    /**
     * People the signed-in user can @mention: the accounts they follow, and
     * only those.
     *
     * Deliberately narrower than the direct-message picker, which also offers
     * people who follow *you*. Replying to someone who messaged you first is
     * reasonable; being able to @mention anyone who followed you is not, because
     * following is one-sided — anyone can follow anyone, so that list is
     * effectively open to strangers, and an autocomplete built on it becomes a
     * way to pull uninvolved people into a thread. Following someone back is the
     * deliberate act that puts them here.
     */
    suspend fun mentionCandidates(): List<FollowUser> {
        val uid = auth.currentUser?.uid ?: return emptyList()

        return try {
            firestore.collection(USERS).document(uid).collection(FOLLOWING)
                .limit(MENTION_CANDIDATE_LIMIT)
                .get().await()
                .documents
                .filter { it.id != uid }
                .map { doc ->
                    FollowUser(
                        uid = doc.id,
                        displayName = doc.getString("displayName").orEmpty()
                            .ifBlank { "Unknown" },
                        avatarUrl = doc.getString("avatarUrl"),
                    )
                }
                .sortedBy { it.displayName.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Everyone following [uid], newest first. */
    fun followersOf(uid: String): Flow<List<FollowUser>> = callbackFlow {
        val reg = firestore.collection(USERS).document(uid)
            .collection(FOLLOWERS)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener
                trySend(
                    snap.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(FollowUser::class.java)?.copy(uid = doc.id)
                        } catch (_: Exception) {
                            null
                        }
                    }.sortedByDescending { it.createdAt }
                )
            }
        awaitClose { reg.remove() }
    }

    /** Live profile document, for the follower/following counts. */
    fun observeUserProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val reg = firestore.collection(USERS).document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(null); return@addSnapshotListener }
                trySend(
                    try {
                        snap?.toObject(UserProfile::class.java)?.copy(uid = uid)
                    } catch (_: Exception) {
                        null
                    }
                )
            }
        awaitClose { reg.remove() }
    }

    /**
     * Follows [target], writing both sides of the relationship and both
     * counters in one transaction — so a follow can never land as half a link
     * or leave a count adrift.
     *
     * Idempotent: following someone twice does nothing the second time.
     */
    suspend fun follow(target: FollowUser): Result<Unit> {
        val me = auth.currentUser ?: return Result.failure(Exception("Not signed in."))
        if (me.uid == target.uid) {
            return Result.failure(Exception("You can't follow yourself."))
        }

        val myRef = firestore.collection(USERS).document(me.uid)
        val targetRef = firestore.collection(USERS).document(target.uid)
        val myName = me.displayName?.takeIf { it.isNotBlank() }
            ?: me.email?.substringBefore('@') ?: "Anonymous"

        return try {
            // Reports whether this was a new follow, so an idempotent repeat
            // doesn't send a second "started following you".
            val isNewFollow = firestore.runTransaction { tx ->
                val edge = myRef.collection(FOLLOWING).document(target.uid)
                if (tx.get(edge).exists()) return@runTransaction false

                tx.set(
                    edge,
                    mapOf(
                        "displayName" to target.displayName,
                        "avatarUrl" to target.avatarUrl,
                        "createdAt" to Timestamp.now(),
                    ),
                )
                tx.set(
                    targetRef.collection(FOLLOWERS).document(me.uid),
                    mapOf(
                        "displayName" to myName,
                        "avatarUrl" to me.photoUrl?.toString(),
                        "createdAt" to Timestamp.now(),
                    ),
                )
                // merge() so the counter lands even if the profile doc is new.
                tx.set(
                    myRef,
                    mapOf("followingCount" to FieldValue.increment(1)),
                    SetOptions.merge(),
                )
                tx.set(
                    targetRef,
                    mapOf("followerCount" to FieldValue.increment(1)),
                    SetOptions.merge(),
                )
                true
            }.await()

            // After the transaction, never inside it: a Firestore transaction
            // can be retried, and a notification written from inside one would
            // be duplicated every time it was.
            if (isNewFollow == true) {
                notifications.notify(
                    recipientId = target.uid,
                    type = NotificationType.FOLLOW,
                    targetId = me.uid,
                    message = "started following you",
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Undoes [follow]. Also idempotent. */
    suspend fun unfollow(targetUid: String): Result<Unit> {
        val me = auth.currentUser ?: return Result.failure(Exception("Not signed in."))

        val myRef = firestore.collection(USERS).document(me.uid)
        val targetRef = firestore.collection(USERS).document(targetUid)

        return try {
            firestore.runTransaction { tx ->
                val edge = myRef.collection(FOLLOWING).document(targetUid)
                if (!tx.get(edge).exists()) return@runTransaction

                tx.delete(edge)
                tx.delete(targetRef.collection(FOLLOWERS).document(me.uid))
                tx.set(
                    myRef,
                    mapOf("followingCount" to FieldValue.increment(-1)),
                    SetOptions.merge(),
                )
                tx.set(
                    targetRef,
                    mapOf("followerCount" to FieldValue.increment(-1)),
                    SetOptions.merge(),
                )
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
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

    /**
     * Rewrites a comment's text and, optionally, its attached photo.
     *
     * [newImageUrl] replaces the existing photo (already uploaded by the
     * caller); [removeImage] drops it entirely. Passing neither leaves whatever
     * photo the comment already had untouched — there's no way to express "set
     * to null" and "don't touch" with a single nullable parameter, hence the
     * separate flag.
     */
    suspend fun updateComment(
        postId: String,
        commentId: String,
        body: String,
        newImageUrl: String? = null,
        removeImage: Boolean = false,
    ): Result<Unit> = try {
        val fields = mutableMapOf<String, Any>(
            "body" to body.trim(),
            "editedAt" to Timestamp.now(),
        )
        if (removeImage) {
            fields["imageUrl"] = FieldValue.delete()
        } else if (newImageUrl != null) {
            fields["imageUrl"] = newImageUrl
        }
        firestore.collection(POSTS).document(postId)
            .collection(COMMENTS).document(commentId)
            .update(fields)
            .await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Removes a comment and keeps the post's counter honest.
     *
     * Replies to a deleted top-level comment are left in place — they'd
     * disappear from the thread view anyway, since it groups by a parent that
     * no longer exists.
     */
    suspend fun deleteComment(postId: String, commentId: String): Result<Unit> = try {
        val postRef = firestore.collection(POSTS).document(postId)
        firestore.runTransaction { tx ->
            tx.delete(postRef.collection(COMMENTS).document(commentId))
            tx.update(postRef, "commentCount", FieldValue.increment(-1))
        }.await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Removes a post and the files it owns in storage. Only the author is
     * allowed to, enforced by rules.
     *
     * Storage goes first, on purpose: the Worker verifies each key against the
     * post document before deleting, so the document has to still be there.
     * If that step fails the post is still removed — orphaned files are untidy,
     * a post that won't delete is a bug the user can see.
     */
    suspend fun deletePost(post: Post): Result<Unit> = try {
        val postRef = firestore.collection(POSTS).document(post.id)

        // Firestore does not cascade: deleting the document would leave the
        // comments, votes and shares underneath it as unreachable orphans, and
        // every image attached to a comment stranded in the bucket. So the
        // whole tree comes down explicitly, deepest first.
        val commentDocs = try {
            postRef.collection(COMMENTS).get().await().documents
        } catch (_: Exception) {
            emptyList()
        }

        val commentImageKeys = commentDocs
            .mapNotNull { storageKeyOf(it.getString("imageUrl")) }

        // Storage before Firestore — the Worker checks each key against the
        // post (and its comments), so both must still exist at this point.
        R2MediaUploader.deleteObjects(post.id, post.storageKeys() + commentImageKeys)

        deleteAllIn(postRef.collection(COMMENTS))
        deleteAllIn(postRef.collection(VOTES))
        deleteAllIn(postRef.collection(SHARES))

        // The post itself goes last, and is the only step allowed to fail the
        // operation. Everything above is tidy-up: leaving a stray vote document
        // behind is invisible to users, whereas a post that refuses to delete
        // is not — so cleanup must never be able to strand it.
        postRef.delete().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Empties a subcollection in batches, best-effort.
     *
     * Chunked because a Firestore write batch tops out at 500 operations, and
     * a popular post can carry more comments than that. Swallows failures on
     * purpose — see the note in [deletePost].
     */
    private suspend fun deleteAllIn(collection: CollectionReference) {
        try {
            while (true) {
                val snapshot = collection.limit(BATCH_LIMIT).get().await()
                if (snapshot.isEmpty) return

                val batch = firestore.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()

                // A short page means that was the last one.
                if (snapshot.size() < BATCH_LIMIT) return
            }
        } catch (_: Exception) {
            // Rules or connectivity — leave the orphans rather than blocking.
        }
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
        /** Every link, in order. Empty clears them. */
        links: List<String>,
        media: List<PostMedia>,
        /** Where the post lives. Blank publishes to the Home Feed. */
        communityId: String = "",
        communityName: String = "",
    ): Result<Unit> = try {
        val cleanLinks = links.map { it.trim() }.filter { it.isNotBlank() }
        // Legacy single-media fields are rewritten too, so the share page and
        // any older client stay consistent with the new attachment list.
        firestore.collection(POSTS).document(postId).update(
            mapOf(
                "title" to title.trim(),
                "body" to body.trim(),
                "linkUrl" to cleanLinks.firstOrNull(),
                "links" to cleanLinks,
                "media" to media.map {
                    hashMapOf("url" to it.url, "type" to it.type, "thumbUrl" to it.thumbUrl)
                },
                "imageUrl" to media.firstOrNull { !it.isVideo }?.url,
                "videoUrl" to media.firstOrNull { it.isVideo }?.url,
                "communityId" to communityId,
                "communityName" to communityName,
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
        /** Every link the author attached, in order. */
        links: List<String> = emptyList(),
        media: List<PostMedia> = emptyList(),
        previewUrl: String? = null,
        previewBlur: String? = null,
        /** Blank publishes to the Home Feed; otherwise the target community. */
        communityId: String = "",
        communityName: String = "",
    ): Result<String> {
        val user = auth.currentUser ?: return Result.failure(Exception("Not signed in."))

        // Normalize the link list, and keep the legacy single field pointed at
        // the first one so older clients and the web worker still show a link.
        val cleanLinks = links.map { it.trim() }.filter { it.isNotBlank() }
            .ifEmpty { listOfNotNull(linkUrl?.trim()?.takeIf { it.isNotBlank() }) }
        val firstLink = cleanLinks.firstOrNull()

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
            "communityId" to communityId,
            "communityName" to communityName,
            "title" to title.trim(),
            "body" to body.trim(),
            "linkUrl" to firstLink,
            "links" to cleanLinks,
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

            // A post inside a community is an announcement to its members —
            // that's the one case where a new post notifies people who didn't
            // interact with it. Home-feed posts notify nobody: the feed is
            // where you go to find them.
            if (communityId.isNotBlank()) {
                notifications.notifyCommunityMembers(
                    communityId = communityId,
                    communityName = communityName,
                    postId = ref.id,
                    message = "posted: ${title.trim().ifBlank { "a new post" }}",
                )
            }

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

        val result = try {
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

        // Only an upvote is worth telling someone about. A downvote arriving as
        // a notification would be a feature for making people feel bad, and
        // no community app ships one.
        if (result == "up") {
            notifyPostAuthor(postId, NotificationType.LIKE, "upvoted your post")
        }
        return result
    }

    /**
     * Sends [message] to whoever wrote [postId].
     *
     * Costs one document read, which is why it runs only after the write it
     * describes has already succeeded — the user's action never waits on it,
     * and a failure here costs a notification rather than the upvote or the
     * comment that produced it.
     */
    private suspend fun notifyPostAuthor(
        postId: String,
        type: NotificationType,
        message: String,
    ) {
        try {
            val authorId = firestore.collection(POSTS).document(postId)
                .get().await().getString("authorId") ?: return
            notifications.notify(
                recipientId = authorId,
                type = type,
                targetId = postId,
                message = message,
            )
        } catch (_: Exception) { /* best-effort */ }
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
        /**
         * Uids the author picked out of the @mention autocomplete.
         *
         * Carried explicitly rather than re-derived from the text, because the
         * text alone can't be parsed reliably: "@Juan Dela Cruz" is
         * indistinguishable from "@Juan" followed by two ordinary words. The
         * picker already knew exactly who was meant, so it says so.
         */
        mentionedUserIds: List<String> = emptyList(),
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
            "mentionedUserIds" to mentionedUserIds,
            "createdAt" to Timestamp.now(),
        )
        return try {
            val postRef = firestore.collection(POSTS).document(postId)
            firestore.runTransaction { tx ->
                tx.update(postRef, "commentCount", FieldValue.increment(1))
                tx.set(postRef.collection(COMMENTS).document(), data)
            }.await()

            notifyAboutComment(postId, body, parentId, mentionedUserIds)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * The Inbox rows one comment can produce: one for whoever it answers, and
     * one for each person named in it.
     *
     * A reply notifies the comment's author instead of the post's — being told
     * "someone commented on your post" when they actually answered a stranger's
     * comment three levels down is noise, and it's the reply's target who
     * actually wants to know.
     */
    private suspend fun notifyAboutComment(
        postId: String,
        body: String,
        parentId: String?,
        mentionedUserIds: List<String>,
    ) {
        if (parentId.isNullOrBlank()) {
            notifyPostAuthor(postId, NotificationType.COMMENT, "commented on your post")
        } else {
            try {
                val parentAuthor = firestore.collection(POSTS).document(postId)
                    .collection(COMMENTS).document(parentId)
                    .get().await().getString("authorId")
                if (parentAuthor != null) {
                    notifications.notify(
                        recipientId = parentAuthor,
                        type = NotificationType.COMMENT,
                        targetId = postId,
                        message = "replied to your comment",
                    )
                }
            } catch (_: Exception) { /* best-effort */ }
        }

        // Picked-from-the-list mentions first, then anything typed by hand that
        // the text scan can still resolve. Unioned so a comment that used both
        // routes notifies each person exactly once.
        val recipients = (mentionedUserIds + resolveMentions(body)).distinct()
        recipients.forEach { mentionedUid ->
            notifications.notify(
                recipientId = mentionedUid,
                type = NotificationType.MENTION,
                targetId = postId,
                message = "mentioned you in a comment",
            )
        }
    }

    /**
     * Turns the `@names` in [body] into uids.
     *
     * Matches a single unbroken run of name characters after the `@`, so
     * "@juan" resolves and "@Juan Dela Cruz" resolves as far as "@Juan". That
     * limitation is deliberate: without a separate unique-handle field there's
     * no way to tell where a multi-word display name ends and the rest of the
     * sentence begins, and guessing would mean querying every prefix of every
     * mention. Adding a `handle` field to `users` is the real fix when
     * mentions matter enough to warrant it.
     *
     * Capped at [MAX_MENTIONS_PER_COMMENT] lookups so a comment full of `@`
     * can't turn into an unbounded burst of queries.
     */
    private suspend fun resolveMentions(body: String): List<String> {
        if (!body.contains('@')) return emptyList()

        val names = MENTION_PATTERN.findAll(body)
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_MENTIONS_PER_COMMENT)
            .toList()

        val uids = mutableListOf<String>()
        names.forEach { name ->
            try {
                firestore.collection(USERS)
                    .whereEqualTo("displayName", name)
                    .limit(1)
                    .get().await()
                    .documents.firstOrNull()
                    ?.let { uids.add(it.id) }
            } catch (_: Exception) { /* best-effort */ }
        }
        return uids.distinct()
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

    // -------------------------------------------------------------------------
    // Hidden posts
    // -------------------------------------------------------------------------

    /**
     * Hides a post from the signed-in user's feeds, for good — a private marker
     * under their own profile.
     */
    suspend fun hidePost(postId: String) {
        val uid = auth.currentUser?.uid ?: return
        try {
            firestore.collection(USERS).document(uid)
                .collection(HIDDEN_POSTS).document(postId)
                .set(hashMapOf("hiddenAt" to Timestamp.now()))
                .await()
        } catch (_: Exception) { /* best-effort */ }
    }

    /**
     * Undoes [hidePost] — removes the marker so the post reappears in the
     * signed-in user's feeds. Reachable from the profile's Comments tab, where a
     * hidden post a user commented on is still visible.
     */
    suspend fun unhidePost(postId: String) {
        val uid = auth.currentUser?.uid ?: return
        try {
            firestore.collection(USERS).document(uid)
                .collection(HIDDEN_POSTS).document(postId)
                .delete()
                .await()
        } catch (_: Exception) { /* best-effort */ }
    }

    /** Whether the signed-in user has hidden [postId]. One-shot — no listener. */
    suspend fun isPostHidden(postId: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            firestore.collection(USERS).document(uid)
                .collection(HIDDEN_POSTS).document(postId)
                .get().await().exists()
        } catch (_: Exception) {
            false
        }
    }

    /** Live set of post ids the signed-in user has hidden. */
    fun observeHiddenPostIds(): Flow<Set<String>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptySet())
            awaitClose { }
            return@callbackFlow
        }
        val registration = firestore.collection(USERS).document(uid)
            .collection(HIDDEN_POSTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot == null) return@addSnapshotListener
                trySend(snapshot.documents.map { it.id }.toSet())
            }
        awaitClose { registration.remove() }
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
        const val USERS = "users"
        const val HIDDEN_POSTS = "hiddenPosts"
        const val FOLLOWERS = "followers"
        const val FOLLOWING = "following"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_SCORE = "score"
        const val FIELD_COMMUNITY_ID = "communityId"
        const val FEED_PAGE_SIZE = 50L

        /** Firestore write batches cap at 500 operations. */
        const val BATCH_LIMIT = 400L

        /**
         * `@` followed by one run of name characters. See [resolveMentions] for
         * why this stops at the first space.
         */
        val MENTION_PATTERN = Regex("@([A-Za-z0-9_.\\-]{2,30})")

        /** Lookup ceiling per comment, so `@@@@@` can't fan out into queries. */
        const val MAX_MENTIONS_PER_COMMENT = 5

        /** How many people the @mention autocomplete pulls from each list. */
        const val MENTION_CANDIDATE_LIMIT = 200L
    }
}