package com.example.kinetixfsl.account

import android.content.Context
import android.util.Log
import com.example.kinetixfsl.auth.AuthRepository
import com.example.kinetixfsl.community.model.storageKeyOf
import com.example.kinetixfsl.community.upload.R2MediaUploader
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Wipes a user's data — the "Reset" and "Delete account" actions in Settings.
 *
 * Best-effort and client-side: each step is guarded, so one failure never
 * aborts the rest. It deletes everything the security rules let a user delete
 * for themselves: their local Room database rows, their
 * `users/{uid}/progress/current` doc,
 * their own posts (with the comments/votes/shares under them), every
 * community they created (with EVERY post inside it, by any author, so a
 * deleted community's content doesn't linger in the feed), their
 * notifications, and their public `users/{uid}` profile.
 *
 * Direct messages (see [deleteOwnChatData]): every message the departing user
 * SENT is deleted and its chat images/videos freed from R2. If the OTHER
 * participant has already deleted their account, the thread is a ghost nobody
 * can open, so the whole conversation — every remaining message and the
 * document itself — is removed too. While the other participant still exists,
 * the thread collapses to their half under a "person no longer available"
 * header and is finished off when they delete in turn.
 *
 * ## What it deliberately does NOT delete (rule-limited)
 *
 * A live thread's shared document and the other participant's messages stay put
 * — one participant doesn't get to wipe a conversation the other still wants.
 */
class AccountEraser(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val authRepository: AuthRepository = AuthRepository(auth),
    /** Reused only for its R2 community-media cleanup during the wipe. */
    private val directory: com.example.kinetixfsl.community.CommunityDirectoryRepository =
        com.example.kinetixfsl.community.CommunityDirectoryRepository(db, auth),
) {

    /** Outcome of a full delete, so the UI can tell the user what happened. */
    enum class DeleteOutcome {
        DELETED,
        DATA_WIPED_NEEDS_REAUTH,
        /** The user backed out of the "confirm it's you" reauth step. Nothing
         *  was touched — data, posts and the Auth account are all intact. */
        REAUTH_CANCELLED,
        FAILED,
    }

    /**
     * Reset: wipe all of this account's data but keep the login alive. After it
     * returns, restart/recreate the UI so screens re-read the empty stores.
     */
    suspend fun resetData(context: Context): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        wipeEverything(context, uid)
    }

    /**
     * Full delete: reauthenticate, wipe the data, then remove the Firebase Auth
     * account itself.
     *
     * ## Why reauthenticate FIRST
     *
     * `FirebaseUser.delete()` requires a "recent" sign-in — a token that's
     * merely valid (silently auto-refreshed, as it normally is during everyday
     * use) doesn't count. A user almost never deletes their account within
     * minutes of originally signing in, so without this step `delete()` throws
     * [FirebaseAuthRecentLoginRequiredException] nearly every time: the data
     * gets wiped, the Auth record survives, and — this was the actual bug —
     * nothing ever retried the deletion on a later sign-in, so the account
     * looked "deleted" but blocked the same Google account from registering
     * fresh. Reauthenticating up front makes the session fresh on purpose, so
     * `delete()` actually succeeds in the normal case instead of leaving an
     * orphaned Auth record behind.
     *
     * Only Google-signed-in accounts are reauthenticated here (that's this
     * app's primary sign-in path); an email/password account still falls back
     * to [DeleteOutcome.DATA_WIPED_NEEDS_REAUTH] as before.
     */
    suspend fun deleteAccount(context: Context): DeleteOutcome {
        val user = auth.currentUser ?: return DeleteOutcome.FAILED

        if (authRepository.isGoogleAccount()) {
            val reauth = authRepository.reauthenticateWithGoogle(context)
            if (reauth is com.example.kinetixfsl.auth.AuthResult.Error) {
                Log.w(TAG, "reauth before delete failed: ${reauth.message}")
                return DeleteOutcome.REAUTH_CANCELLED
            }
        }

        val uid = user.uid
        runCatching { wipeEverything(context, uid) }
            .onFailure { Log.w(TAG, "data wipe had failures (continuing)", it) }
        return try {
            user.delete().await()
            DeleteOutcome.DELETED
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            // Rare now that we reauthenticate up front (e.g. the fresh session
            // itself expired in the few seconds it took to wipe Firestore data).
            DeleteOutcome.DATA_WIPED_NEEDS_REAUTH
        } catch (e: Exception) {
            Log.e(TAG, "auth delete failed", e)
            DeleteOutcome.FAILED
        }
    }

    // ── the wipe ────────────────────────────────────────────────────────────

    private suspend fun wipeEverything(context: Context, uid: String) {
        clearLocalProgress(context, uid)
        // Progress lives at users/{uid}/progress/current (the restructure's
        // Phase 3 target). This is technically redundant with the recursive
        // subcollection sweep below, but kept explicit for clarity and as a
        // belt-and-suspenders in case that sweep's list of subcollections ever
        // drifts.
        runCatching {
            db.collection(USERS).document(uid)
                .collection(PROGRESS).document(PROGRESS_DOC).delete().await()
        }.onFailure { Log.w(TAG, "progress delete failed", it) }
        deleteOwnPosts(uid)
        deleteOwnCommunities(uid)
        // Deterministic first pass: clear everything this user wrote into other
        // people's subtrees using the manifest (comments, votes, shares, outgoing
        // notifications), repairing each affected post's counters. No dependence
        // on collection-group indexes. See AuthoredManifest and §4.2 of the plan.
        deleteViaManifest(uid)
        // Fallback sweep for anything the manifest missed (older clients, a
        // failed companion write). Both use RECOUNT, so running after the manifest
        // pass is idempotent — a post already fixed is simply set to the same value.
        deleteOwnCommentsEverywhere(uid)
        deleteOwnVotesEverywhere(uid)
        // Direct messages and their R2 media — and the whole thread once both
        // participants have deleted their accounts.
        deleteOwnChatData(uid)
        // Reciprocal cleanup — must run BEFORE deleteUserProfile, which clears
        // this user's own following/followers/joined lists that these read.
        leaveJoinedCommunities(uid)
        clearFollowGraph(uid)
        deleteNotifications(uid)
        deleteUserProfile(uid)
    }

    /**
     * Clears this account's local offline data: the Room/SQLite rows for its uid
     * (progress, activity log, quiz game), its saved local profile name/avatar
     * override (see [com.example.kinetixfsl.profile.LocalProfileStore]), plus
     * any leftover legacy SharedPreferences files from before the Room
     * migration.
     */
    private fun clearLocalProgress(context: Context, uid: String) {
        runCatching {
            val db = com.example.kinetixfsl.data.local.KinetixDatabase.get(context)
            db.progressDao().wipe(uid)
            db.activityDao().wipe(uid)
            db.quizDao().wipe(uid)
        }.onFailure { Log.w(TAG, "local Room wipe failed", it) }

        runCatching {
            com.example.kinetixfsl.profile.LocalProfileStore.clear(context, uid)
        }.onFailure { Log.w(TAG, "local profile override clear failed", it) }

        // Legacy prefs (harmless if already migrated/removed).
        listOf("kinetix_progress__$uid", "kinetix_activity__$uid", "quiz_game__$uid")
            .forEach { name ->
                runCatching {
                    context.applicationContext
                        .getSharedPreferences(name, Context.MODE_PRIVATE)
                        .edit().clear().apply()
                }
            }
    }

    /** Deletes every post the user authored, tree-first (comments/votes/shares). */
    private suspend fun deleteOwnPosts(uid: String) = runCatching {
        val posts = db.collection(POSTS).whereEqualTo("authorId", uid).get().await()
        for (doc in posts.documents) {
            val ref = doc.reference
            // R2 first: the Worker verifies each key against the still-present
            // post (and its comments) before deleting, so this has to run before
            // any of those documents come down. See [deletePostMedia].
            deletePostMedia(doc)
            deleteAllIn(ref.collection(COMMENTS))
            deleteAllIn(ref.collection(VOTES))
            deleteAllIn(ref.collection(SHARES))
            runCatching { ref.delete().await() }
        }
    }.onFailure { Log.w(TAG, "own-posts delete failed", it) }.let { }

    /**
     * Deletes communities this user created — and crucially, EVERY post inside
     * them (by any author, not just this user), members, then the community doc
     * itself. Without the post sweep, the community's content used to linger in
     * the feed after the owner deleted their account.
     */
    private suspend fun deleteOwnCommunities(uid: String) = runCatching {
        val comms = db.collection(COMMUNITIES).whereEqualTo("creatorId", uid).get().await()
        for (doc in comms.documents) {
            deletePostsInCommunity(doc.id)
            deleteAllIn(doc.reference.collection(MEMBERS))
            // The community's own avatar/banner from R2, while the doc still
            // exists so the Worker can authorise the keys against it.
            runCatching { directory.deleteCommunityMedia(doc.id) }
            runCatching { doc.reference.delete().await() }
        }
    }.onFailure { Log.w(TAG, "own-communities delete failed", it) }.let { }

    /**
     * Removes every comment this user wrote on ANY post — others' posts
     * included — and corrects each affected post's `commentCount`. Their own
     * posts' comments are already gone with the posts, so this reaches the
     * comments they left elsewhere, which used to linger under a deleted name
     * and used to leave the post's comment tally inflated.
     *
     * The counter is not decremented — it's RECOUNTED: after this user's
     * comments come off a post, the post's `commentCount` is set to the number
     * of comment documents actually left under it. That's deliberately not a
     * transaction and not an `increment(-n)` — a relative decrement only lands
     * the count on the right number if it started on the right number, and an
     * earlier `increment` attempt here was the step that wasn't sticking.
     * Setting the exact value is self-correcting: it fixes the post even if the
     * count had already drifted.
     *
     * Paged, because a chatty account can exceed one response.
     */
    private suspend fun deleteOwnCommentsEverywhere(uid: String) = runCatching {
        while (true) {
            val snap = db.collectionGroup(COMMENTS)
                .whereEqualTo("authorId", uid)
                .limit(BATCH.toLong())
                .get().await()
            if (snap.isEmpty) break

            // Group this page's comments by the post they hang off, so each
            // affected post is recounted once, not once per comment removed.
            val byPost = snap.documents.groupBy { it.reference.parent.parent }
            for ((postRef, comments) in byPost) {
                comments.forEach { runCatching { it.reference.delete().await() } }
                if (postRef == null) continue
                runCatching {
                    val remaining = postRef.collection(COMMENTS).get().await().size().toLong()
                    postRef.update("commentCount", remaining).await()
                }
            }
            if (snap.size() < BATCH) break
        }
    }.onFailure { Log.w(TAG, "comments-everywhere delete failed", it) }.let { }

    /**
     * Removes every vote this user cast on ANY post and corrects that post's
     * up/downvote count and score, so a departing account doesn't leave inflated
     * tallies behind. Relies on the `userId` field written with each vote (see
     * CommunityRepository.vote) — votes cast before that field existed can't be
     * found this way and are left alone.
     *
     * Like [deleteOwnCommentsEverywhere], the counts are RECOUNTED rather than
     * decremented: after this user's votes come off a post, `upvoteCount` /
     * `downvoteCount` are set from the vote documents still under it, and
     * `score` from their difference. Setting exact values lands the post on the
     * right numbers even if a previous decrement had been lost.
     */
    private suspend fun deleteOwnVotesEverywhere(uid: String) = runCatching {
        while (true) {
            val snap = db.collectionGroup(VOTES)
                .whereEqualTo("userId", uid)
                .limit(BATCH.toLong())
                .get().await()
            if (snap.isEmpty) break

            val byPost = snap.documents.groupBy { it.reference.parent.parent }
            for ((postRef, votes) in byPost) {
                votes.forEach { runCatching { it.reference.delete().await() } }
                if (postRef == null) continue
                runCatching {
                    val left = postRef.collection(VOTES).get().await().documents
                    val up = left.count { it.getString("direction") == "up" }.toLong()
                    val down = left.count { it.getString("direction") == "down" }.toLong()
                    postRef.update(
                        mapOf(
                            "upvoteCount" to up,
                            "downvoteCount" to down,
                            "score" to up - down,
                        ),
                    ).await()
                }
            }
            if (snap.size() < BATCH) break
        }
    }.onFailure { Log.w(TAG, "votes-everywhere delete failed", it) }.let { }

    /**
     * Cleans this user out of every direct-message thread they're in, and frees
     * the chat images/videos involved from R2.
     *
     * Two cases per thread, decided by whether the OTHER participant still has a
     * `users/{uid}` doc:
     *
     *  - **They're still around** — delete only the messages this user sent
     *    (the rules let a sender delete their own), and free the R2 media on
     *    them. The thread lives on as the other person's half, under a "person
     *    no longer available" header.
     *  - **They've already deleted their account too** — the thread is a ghost
     *    nobody can see: delete EVERY remaining message, free all their R2
     *    media, then delete the conversation document itself. The rules allow a
     *    participant to clear a thread once the other side's account is gone.
     *
     * R2 first, while the messages still exist — the Worker authorises chat keys
     * by the thread's two participant folders, and once the docs are gone
     * there's nothing tying a key to this thread.
     */
    private suspend fun deleteOwnChatData(uid: String) = runCatching {
        val threads = db.collection(CONVERSATIONS)
            .whereArrayContains("participants", uid)
            .get().await()

        for (thread in threads.documents) {
            val otherUid = (thread.get("participants") as? List<*>)
                ?.mapNotNull { it as? String }
                ?.firstOrNull { it != uid }
            val otherGone = otherUid == null || runCatching {
                !db.collection(USERS).document(otherUid).get().await().exists()
            }.getOrDefault(false)

            val base = thread.reference.collection(MESSAGES)
            val query = if (otherGone) base else base.whereEqualTo("senderId", uid)

            while (true) {
                val page = runCatching { query.limit(BATCH.toLong()).get().await() }
                    .getOrNull() ?: break
                if (page.isEmpty) break

                val keys = page.documents.flatMap { m ->
                    listOfNotNull(
                        storageKeyOf(m.getString("mediaUrl")),
                        storageKeyOf(m.getString("thumbUrl")),
                    )
                }.distinct()
                if (keys.isNotEmpty()) {
                    runCatching { R2MediaUploader.deleteConversationObjects(thread.id, keys) }
                }

                for (m in page.documents) runCatching { m.reference.delete().await() }
                if (page.size() < BATCH) break
            }

            // No one left to keep it — take the whole thread down.
            if (otherGone) {
                runCatching { thread.reference.delete().await() }
            }
        }
    }.onFailure { Log.w(TAG, "chat data cleanup failed", it) }.let { }

    /**
     * Removes this user from every community they merely *joined* (created by
     * someone else): deletes their `members/{uid}` marker and decrements that
     * community's memberCount. Communities they created are already gone via
     * [deleteOwnCommunities], so those become harmless no-ops here.
     *
     * Without this, a deleted account stayed listed in the rosters of every
     * community it had joined, and those communities' member counts stayed
     * inflated.
     */
    private suspend fun leaveJoinedCommunities(uid: String) = runCatching {
        val joined = db.collection(USERS).document(uid)
            .collection(JOINED_COMMUNITIES).get().await()
        for (doc in joined.documents) {
            val communityId = doc.id
            val communityRef = db.collection(COMMUNITIES).document(communityId)
            runCatching {
                communityRef.collection(MEMBERS).document(uid).delete().await()
            }
            // Best-effort count fix. `update` (not `set`) so a community that's
            // already gone is a no-op rather than being recreated as a ghost.
            runCatching {
                communityRef.update("memberCount", FieldValue.increment(-1)).await()
            }
        }
    }.onFailure { Log.w(TAG, "leave-joined-communities failed", it) }.let { }

    /**
     * Severs every follow connection in both directions, so no surviving account
     * is left following — or listed as followed by — this deleted one.
     *
     * For each account this user follows, removes this user from that account's
     * followers and drops its followerCount. For each account that follows this
     * user, removes this user from that account's following list and drops its
     * followingCount. This user's own following/followers subcollections are
     * cleared afterwards by [deleteUserProfile].
     */
    private suspend fun clearFollowGraph(uid: String) {
        val userRef = db.collection(USERS).document(uid)

        // Accounts this user follows → take this user out of their followers.
        runCatching {
            val following = userRef.collection(FOLLOWING).get().await()
            for (doc in following.documents) {
                val targetRef = db.collection(USERS).document(doc.id)
                runCatching { targetRef.collection(FOLLOWERS).document(uid).delete().await() }
                // `update` so an account that's also been deleted isn't recreated.
                runCatching { targetRef.update("followerCount", FieldValue.increment(-1)).await() }
            }
        }.onFailure { Log.w(TAG, "clear outgoing follows failed", it) }

        // Accounts that follow this user → take this user out of their following.
        runCatching {
            val followers = userRef.collection(FOLLOWERS).get().await()
            for (doc in followers.documents) {
                val followerRef = db.collection(USERS).document(doc.id)
                runCatching { followerRef.collection(FOLLOWING).document(uid).delete().await() }
                runCatching { followerRef.update("followingCount", FieldValue.increment(-1)).await() }
            }
        }.onFailure { Log.w(TAG, "clear incoming follows failed", it) }
    }

    /** Deletes every post in a community (any author), tree-first. */
    private suspend fun deletePostsInCommunity(communityId: String) = runCatching {
        val posts = db.collection(POSTS)
            .whereEqualTo(FIELD_COMMUNITY_ID, communityId).get().await()
        for (doc in posts.documents) {
            val ref = doc.reference
            // R2 first — same reason as in [deleteOwnPosts]. These posts can be
            // by any author; the Worker authorises by the post's own media URLs,
            // not by who is asking, so a community owner's sweep still cleans up
            // members' files.
            deletePostMedia(doc)
            deleteAllIn(ref.collection(COMMENTS))
            deleteAllIn(ref.collection(VOTES))
            deleteAllIn(ref.collection(SHARES))
            runCatching { ref.delete().await() }
        }
    }.onFailure { Log.w(TAG, "community-posts delete failed", it) }.let { }

    /**
     * Removes a post's files from Cloudflare R2 — every version of each image
     * (full, feed copy, share preview), each video, and any images attached to
     * its comments.
     *
     * Must run while the post and its comments still exist: the upload Worker
     * checks each key against that post's own URLs before deleting, so it can't
     * be coerced into wiping unrelated objects. Best-effort — a failure here
     * only leaves orphaned files, and must never stop the account wipe.
     */
    private suspend fun deletePostMedia(postDoc: DocumentSnapshot) = runCatching {
        val keys = postStorageKeys(postDoc).toMutableList()

        // Images attached to this post's comments belong to it too, and go with
        // it — so they're collected here and counted as owned by the Worker.
        val commentDocs = runCatching {
            postDoc.reference.collection(COMMENTS).get().await().documents
        }.getOrDefault(emptyList())
        commentDocs.forEach { c ->
            storageKeyOf(c.getString("imageUrl"))?.let { keys += it }
        }

        if (keys.isNotEmpty()) {
            R2MediaUploader.deleteObjects(postDoc.id, keys.distinct())
        }
    }.onFailure { Log.w(TAG, "R2 media delete failed for ${postDoc.id}", it) }.let { }

    /**
     * Every file a post owns in storage, as bucket keys — mirrors
     * [com.example.kinetixfsl.community.model.Post.storageKeys], read straight
     * off the raw snapshot so this doesn't depend on a full Post mapping.
     */
    private fun postStorageKeys(doc: DocumentSnapshot): List<String> {
        val urls = buildList {
            add(doc.getString("imageUrl"))
            add(doc.getString("videoUrl"))
            add(doc.getString("previewUrl"))
            (doc.get("media") as? List<*>)?.forEach { item ->
                val m = item as? Map<*, *> ?: return@forEach
                add(m["url"] as? String)
                add(m["thumbUrl"] as? String)
            }
        }
        return urls.mapNotNull { storageKeyOf(it) }.distinct()
    }

    /**
     * Removes the user's public profile outright so a later sign-in starts from
     * a fresh default instead of the deleted account's name/photo — no lingering
     * "Anonymous" record. Its avatar and banner go from Cloudflare R2 too, and
     * every subcollection the owner is allowed to clear comes down first.
     *
     * Best-effort: if a client can't complete the document delete (rules, or an
     * offline write that never lands), it falls back to blanking the identifying
     * fields so nothing recognisable is left behind either way.
     */
    private suspend fun deleteUserProfile(uid: String) {
        val ref = db.collection(USERS).document(uid)

        // R2 first, while the document still exists: the Worker verifies the
        // avatar/banner keys against users/{uid} before deleting them.
        deleteProfileMedia(ref)

        // Subcollections the owner's own rules permit deleting. Others
        // (followers, blockedBy — written by *other* users) need an admin sweep
        // and are left to the moderation tooling.
        OWNED_SUBCOLLECTIONS.forEach { name ->
            runCatching { deleteAllIn(ref.collection(name)) }
        }

        val deleted = runCatching { ref.delete().await() }.isSuccess
        if (!deleted) {
            runCatching {
                ref.set(
                    mapOf("displayName" to "Anonymous", "avatarUrl" to null, "bannerUrl" to null),
                    SetOptions.merge(),
                ).await()
            }.onFailure { Log.w(TAG, "profile blank fallback failed", it) }
        }
    }

    /**
     * Deletes the profile's avatar and banner from Cloudflare R2. Best-effort —
     * a failure here only orphans those two files and must never stop the wipe.
     */
    private suspend fun deleteProfileMedia(ref: com.google.firebase.firestore.DocumentReference) =
        runCatching {
            val snap = ref.get().await()
            val keys = listOfNotNull(
                storageKeyOf(snap.getString("avatarUrl")),
                storageKeyOf(snap.getString("bannerUrl")),
            ).distinct()
            if (keys.isNotEmpty()) {
                R2MediaUploader.deleteProfileObjects(ref.id, keys)
            }
        }.onFailure { Log.w(TAG, "profile media delete failed", it) }.let { }

    /**
     * Consumes the deletion manifest (`users/{uid}/authored`): deletes every
     * cross-user document this user wrote — comments, votes, shares, and the
     * outgoing notifications they left in other inboxes — then repairs the
     * post-scoped counters on each affected parent by RECOUNTING its surviving
     * children (self-correcting, same as the fallback sweeps).
     *
     * Follower counters are left to [clearFollowGraph] and community member
     * counters to [leaveJoinedCommunities]: those iterate this user's own lists
     * and decrement once, so repairing them here too would double-count. Manifest
     * rows of those types only delete their target document.
     */
    private suspend fun deleteViaManifest(uid: String) = runCatching {
        val authored = db.collection(USERS).document(uid).collection(AUTHORED)
        // parentPath -> manifest type, so each affected parent is recounted once.
        val toRepair = mutableMapOf<String, String>()

        while (true) {
            val snap = authored.limit(BATCH.toLong()).get().await()
            if (snap.isEmpty) break
            for (doc in snap.documents) {
                val path = doc.getString("path")
                if (path.isNullOrBlank()) { runCatching { doc.reference.delete().await() }; continue }
                val type = doc.getString("type") ?: ""
                val parentPath = doc.getString("parentPath")
                runCatching { db.document(path).delete().await() }
                if (!parentPath.isNullOrBlank() &&
                    type in setOf(TYPE_COMMENT, TYPE_VOTE, TYPE_COMMENT_VOTE, TYPE_SHARE)
                ) {
                    toRepair[parentPath] = type
                }
                runCatching { doc.reference.delete().await() }
            }
            if (snap.size() < BATCH) break
        }

        for ((parentPath, type) in toRepair) {
            val parentRef = db.document(parentPath)
            runCatching {
                when (type) {
                    TYPE_COMMENT -> {
                        val remaining = parentRef.collection(COMMENTS).get().await().size().toLong()
                        parentRef.update("commentCount", remaining).await()
                    }
                    TYPE_SHARE -> {
                        val remaining = parentRef.collection(SHARES).get().await().size().toLong()
                        parentRef.update("shareCount", remaining).await()
                    }
                    TYPE_VOTE, TYPE_COMMENT_VOTE -> {
                        val left = parentRef.collection(VOTES).get().await().documents
                        val up = left.count { it.getString("direction") == "up" }.toLong()
                        val down = left.count { it.getString("direction") == "down" }.toLong()
                        parentRef.update(
                            mapOf("upvoteCount" to up, "downvoteCount" to down, "score" to up - down),
                        ).await()
                    }
                }
            }
        }
    }.onFailure { Log.w(TAG, "manifest sweep failed", it) }.let { }

    /** Empties this user's notification inbox — both the new nested path and the old root. */
    private suspend fun deleteNotifications(uid: String) = runCatching {
        runCatching {
            deleteAllIn(db.collection(USERS).document(uid).collection(NOTIFICATIONS_SUB))
        }
        // OLD PATH (Phase 5: remove).
        runCatching { deleteAllIn(db.collection(NOTIFICATIONS).document(uid).collection(ITEMS)) }
    }.onFailure { Log.w(TAG, "notifications delete failed", it) }.let { }

    /** Deletes a whole (sub)collection in batches; best-effort. */
    private suspend fun deleteAllIn(collection: CollectionReference) {
        try {
            while (true) {
                val snap = collection.limit(BATCH.toLong()).get().await()
                if (snap.isEmpty) break
                for (d in snap.documents) runCatching { d.reference.delete().await() }
                if (snap.size() < BATCH) break
            }
        } catch (e: Exception) {
            Log.w(TAG, "batch delete of ${collection.path} failed", e)
        }
    }

    private companion object {
        const val TAG = "AccountEraser"
        const val PROGRESS = "progress"
        /** Doc id of the single nested progress doc: users/{uid}/progress/current. */
        const val PROGRESS_DOC = "current"
        const val POSTS = "posts"
        const val VOTES = "votes"
        const val SHARES = "shares"
        const val COMMENTS = "comments"
        const val COMMUNITIES = "communities"
        const val MEMBERS = "members"
        const val JOINED_COMMUNITIES = "joinedCommunities"
        const val FOLLOWING = "following"
        const val FOLLOWERS = "followers"
        const val NOTIFICATIONS = "notifications"       // old root collection
        const val NOTIFICATIONS_SUB = "notifications"   // users/{uid}/notifications (new)
        const val ITEMS = "items"
        const val AUTHORED = AuthoredManifest.COLLECTION
        const val CONVERSATIONS = "conversations"
        const val MESSAGES = "messages"
        const val USERS = "users"
        const val FIELD_COMMUNITY_ID = "communityId"
        const val BATCH = 300

        // Manifest types — aliased from AuthoredManifest for readable when-arms.
        const val TYPE_COMMENT = AuthoredManifest.TYPE_COMMENT
        const val TYPE_VOTE = AuthoredManifest.TYPE_VOTE
        const val TYPE_COMMENT_VOTE = AuthoredManifest.TYPE_COMMENT_VOTE
        const val TYPE_SHARE = AuthoredManifest.TYPE_SHARE

        /**
         * Subcollections under `users/{uid}` cleared when the account is wiped.
         * Every one must be deletable by the owner under the security rules —
         * `followers` and `blockedBy` are written by *other* users, but the
         * rules now let the profile owner clear them too, precisely so a deleted
         * account leaves no subcollection behind to keep it showing as a ghost
         * document. All of these must be swept, or the parent user doc lingers.
         *
         * (Was once a single wrong name, "joined" — the real one is
         * "joinedCommunities" — so those docs were never actually removed.)
         */
        val OWNED_SUBCOLLECTIONS = listOf(
            "joinedCommunities",
            "following",
            "followers",
            "hiddenPosts",
            "recentCommunities",
            "blocked",
            "blockedBy",
            "devices",
            // Restructure additions — all owner-deletable subcollections that now
            // live under users/{uid}, so a self-delete leaves no ghost subtree.
            // (`status/moderation` is admin-write-only by design, so the owner
            // can't clear it — an admin sweep does, same as the old accountStatus
            // root doc. `progress` and the notification inbox are cleared above.)
            AuthoredManifest.COLLECTION,   // authored — the deletion manifest itself
            "private",                     // private/otp
        )
    }
}
