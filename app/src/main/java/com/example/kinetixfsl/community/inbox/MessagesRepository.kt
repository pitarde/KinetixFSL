package com.example.kinetixfsl.community.inbox

import com.example.kinetixfsl.community.inbox.model.ChatMessage
import com.example.kinetixfsl.community.inbox.model.Conversation
import com.example.kinetixfsl.community.inbox.model.NotificationType
import com.example.kinetixfsl.community.inbox.model.conversationIdFor
import com.example.kinetixfsl.community.inbox.model.readIsRead
import com.example.kinetixfsl.community.model.storageKeyOf
import com.example.kinetixfsl.community.upload.R2MediaUploader
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
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
 * Direct messages — `conversations/{id}` and the `messages` underneath it.
 *
 * A thread's id is derived from the two uids ([conversationIdFor]) rather than
 * generated, so both people compute the same one and there's no window where
 * simultaneous first messages create two separate threads.
 *
 * The conversation document carries a denormalised copy of the last message,
 * both display names and both photos. That's what makes the inbox list a single
 * query: rendering twenty rows never touches the twenty message subcollections
 * or the twenty user documents behind them.
 */
class MessagesRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val notifications: NotificationRepository = NotificationRepository(),
) {

    val currentUid: String? get() = auth.currentUser?.uid

    // -------------------------------------------------------------------------
    // Inbox list
    // -------------------------------------------------------------------------

    /**
     * Every thread the signed-in user is in, most recent first.
     *
     * Sorted in Kotlin rather than with `orderBy`, because pairing an
     * `arrayContains` filter with an ordering needs a composite index — and a
     * single user's thread list is small enough that sorting it here costs
     * nothing. Same trade-off the profile's post list already makes.
     */
    fun observeConversations(): Flow<List<Conversation>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = firestore.collection(CONVERSATIONS)
            .whereArrayContains(FIELD_PARTICIPANTS, uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                if (snapshot == null) return@addSnapshotListener
                trySend(
                    snapshot.documents
                        .mapNotNull { it.toConversationOrNull() }
                        // A thread the user deleted stays out of their inbox
                        // until the other person messages them again, which
                        // clears the flag. Filtered here rather than in the
                        // query because Firestore can't express "map key not
                        // true", and a single user's thread list is small.
                        .filter { !it.isHiddenFor(uid) }
                        // Nothing to preview yet? Then it's an empty shell (its
                        // only messages predate this user's clear cutoff) — keep
                        // it out of the list until there's a message to show.
                        .filter { it.lastMessageTime != null }
                        // A brand-new thread has no server timestamp yet; it
                        // sorts to the top, which is where the user just put it.
                        .sortedByDescending { it.lastMessageTime?.toDate()?.time ?: Long.MAX_VALUE }
                )
            }
        awaitClose { registration.remove() }
    }

    /** Live view of one thread — the header's name, photo and typing state. */
    fun observeConversation(conversationId: String): Flow<Conversation?> = callbackFlow {
        val registration = firestore.collection(CONVERSATIONS).document(conversationId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(null); return@addSnapshotListener }
                trySend(snapshot?.toConversationOrNull())
            }
        awaitClose { registration.remove() }
    }

    // -------------------------------------------------------------------------
    // One thread
    // -------------------------------------------------------------------------

    /**
     * The messages in [conversationId], oldest first — which is the order a
     * chat reads in, top to bottom.
     *
     * Capped at [MESSAGE_PAGE_SIZE] of the *newest* messages: the query orders
     * descending to take the right end of the thread, then flips the page back
     * into reading order.
     */
    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = callbackFlow {
        val registration = firestore.collection(CONVERSATIONS).document(conversationId)
            .collection(MESSAGES)
            .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .limit(MESSAGE_PAGE_SIZE)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                if (snapshot == null) return@addSnapshotListener
                trySend(
                    snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(ChatMessage::class.java)?.copy(
                                id = doc.id,
                                // Not something toObject can fill in — see readIsRead.
                                isRead = doc.readIsRead(),
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }.reversed()
                )
            }
        awaitClose { registration.remove() }
    }

    /**
     * The id of the thread with [otherUid] — **without creating it**.
     *
     * Opening a chat no longer writes anything. A thread the user opened but
     * never sent to used to show up in the inbox as an empty row, because the
     * open wrote a participants-only document that the inbox query then
     * returned. Now the document is created lazily by the first [sendMessage],
     * so a conversation appears in the list exactly when it has a message in it
     * — the way every messenger behaves.
     *
     * The chat screen fills its header from [userBrief] while the document
     * doesn't exist yet, so nothing is lost by deferring the write.
     */
    fun conversationIdWith(otherUid: String): Result<String> {
        val me = auth.currentUser ?: return Result.failure(Exception("You're not signed in."))
        if (otherUid.isBlank() || otherUid == me.uid) {
            return Result.failure(Exception("You can't message yourself."))
        }
        return Result.success(conversationIdFor(me.uid, otherUid))
    }

    /**
     * Sends one message and moves everything that depends on it in the same
     * transaction: the thread's preview line, its timestamp, and the
     * recipient's unread count.
     *
     * Doing those as separate writes would leave visible half-states — a
     * message showing in the thread while the inbox row still previews the
     * previous one, or an unread badge that never arrives.
     */
    suspend fun sendMessage(
        conversationId: String,
        recipientId: String,
        text: String,
        mediaUrl: String? = null,
        mediaType: String? = null,
        /** Still frame for a video attachment. See [ChatMessage.thumbUrl]. */
        thumbUrl: String? = null,
    ): Result<Unit> {
        val me = auth.currentUser ?: return Result.failure(Exception("You're not signed in."))
        val body = text.trim()
        if (body.isBlank() && mediaUrl.isNullOrBlank()) return Result.success(Unit)

        val conversationRef = firestore.collection(CONVERSATIONS).document(conversationId)
        val messageRef = conversationRef.collection(MESSAGES).document()

        // What the inbox row shows when the message is a photo or a clip —
        // there's no text to preview, so name the attachment instead.
        val preview = when {
            body.isNotBlank() -> body
            mediaType == "video" -> "Sent a video"
            else -> "Sent a photo"
        }

        val myName = me.displayName?.takeIf { it.isNotBlank() }
            ?: me.email?.substringBefore('@') ?: "Anonymous"

        return try {
            firestore.runTransaction { tx ->
                // All reads before any write — Firestore's transaction rule.
                val convoSnap = tx.get(conversationRef)
                val firstMessage = !convoSnap.exists()

                // The conversation document is born here, on the first message,
                // not when the chat was opened. On first send we also have to
                // stamp the participant list and both people's denormalised name
                // and photo, which the inbox renders straight from — read the
                // recipient's profile in the same transaction to get theirs.
                val identity: Map<String, Any?> = if (firstMessage) {
                    val otherSnap = tx.get(firestore.collection(USERS).document(recipientId))
                    mapOf(
                        "participants" to listOf(me.uid, recipientId).sorted(),
                        "participantNames" to mapOf(
                            me.uid to myName,
                            recipientId to (otherSnap.getString("displayName")
                                ?.takeIf { it.isNotBlank() } ?: "Unknown"),
                        ),
                        "participantPhotos" to mapOf(
                            me.uid to me.photoUrl?.toString(),
                            recipientId to otherSnap.getString("avatarUrl"),
                        ),
                    )
                } else {
                    emptyMap()
                }

                tx.set(
                    messageRef,
                    hashMapOf(
                        "senderId" to me.uid,
                        "text" to body,
                        "mediaUrl" to mediaUrl,
                        "mediaType" to mediaType,
                        "thumbUrl" to thumbUrl,
                        "isRead" to false,
                        "createdAt" to Timestamp.now(),
                    ),
                )
                tx.set(
                    conversationRef,
                    identity + mapOf(
                        "lastMessage" to preview,
                        "lastMessageSenderId" to me.uid,
                        "lastMessageTime" to Timestamp.now(),
                        // Only the recipient's tally moves. Ours is already
                        // zero — we're looking at the thread.
                        "unreadCount" to mapOf(recipientId to FieldValue.increment(1)),
                        // Sending ends typing, so the other side's "typing…"
                        // can't be left stuck on after the message lands.
                        "typing" to mapOf(me.uid to false),
                        // Un-hide for both sides. If either had cleared the
                        // thread, a new message brings it back into their inbox —
                        // the Messenger behaviour where messaging someone again
                        // re-opens the conversation you'd deleted.
                        "hiddenFor" to mapOf(me.uid to false, recipientId to false),
                    ),
                    SetOptions.merge(),
                )
            }.await()

            // The in-app notification row. Separate from the message write on
            // purpose: it belongs to the recipient's notification tree, not to
            // this thread, and it must never be able to fail the send.
            notifications.notify(
                recipientId = recipientId,
                type = NotificationType.MESSAGE,
                targetId = conversationId,
                message = "sent you a message",
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Rewrites the signed-in user's denormalised name and photo across every
     * thread they're in.
     *
     * Necessary because a conversation carries a copy of both participants'
     * details so the inbox list renders in one query — the cost of that
     * denormalisation is that a rename has to be pushed out to each copy, or
     * the other person's inbox keeps showing the old name forever.
     */
    suspend fun propagateProfile(displayName: String, avatarUrl: String?) {
        val uid = auth.currentUser?.uid ?: return
        try {
            val threads = firestore.collection(CONVERSATIONS)
                .whereArrayContains(FIELD_PARTICIPANTS, uid)
                .get().await()
                .documents

            threads.chunked(BATCH_LIMIT).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { doc ->
                    // Nested maps merge key by key, so this touches only our own
                    // entry and leaves the other participant's alone.
                    batch.set(
                        doc.reference,
                        mapOf(
                            "participantNames" to mapOf(uid to displayName),
                            "participantPhotos" to mapOf(uid to avatarUrl),
                        ),
                        SetOptions.merge(),
                    )
                }
                batch.commit().await()
            }
        } catch (_: Exception) { /* best-effort */ }
    }

    /**
     * Clears the signed-in user's unread count for this thread and stamps the
     * other side's messages as seen. Called when the chat screen opens and
     * whenever a message arrives while it's still open.
     *
     * The per-message flag is what the "Seen" receipt under the last outgoing
     * bubble reads; the conversation counter is what the badges read. Both have
     * to move or one of the two indicators goes stale.
     */
    suspend fun markConversationRead(conversationId: String) {
        val uid = auth.currentUser?.uid ?: return
        val conversationRef = firestore.collection(CONVERSATIONS).document(conversationId)
        try {
            conversationRef.set(
                mapOf("unreadCount" to mapOf(uid to 0L)),
                SetOptions.merge(),
            ).await()

            val unread = conversationRef.collection(MESSAGES)
                .whereEqualTo("isRead", false)
                .limit(BATCH_LIMIT.toLong())
                .get().await()
                .documents
                // Ours are already "read" by definition — only the incoming
                // ones get a receipt.
                .filter { it.getString("senderId") != uid }

            if (unread.isEmpty()) return
            val batch = firestore.batch()
            unread.forEach { batch.set(it.reference, mapOf("isRead" to true), SetOptions.merge()) }
            batch.commit().await()
        } catch (_: Exception) { /* best-effort */ }
    }

    /**
     * Publishes whether the signed-in user is currently typing.
     *
     * A plain field on the conversation document rather than a separate
     * collection: the chat screen is already listening to that document for the
     * header, so the indicator costs no extra listener.
     */
    suspend fun setTyping(conversationId: String, isTyping: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        try {
            firestore.collection(CONVERSATIONS).document(conversationId).set(
                mapOf("typing" to mapOf(uid to isTyping)),
                SetOptions.merge(),
            ).await()
        } catch (_: Exception) { /* best-effort */ }
    }

    // -------------------------------------------------------------------------
    // Deleting
    // -------------------------------------------------------------------------

    /**
     * Deletes a thread for the signed-in user only — the Messenger behaviour.
     *
     * The conversation vanishes from *their* inbox and *their* copy of the
     * history goes with it, while the other person keeps both. This is done with
     * two per-user flags rather than by deleting anything shared:
     *
     *  - `hiddenFor[me] = true` takes the row out of my inbox (the list filters
     *    it out client-side).
     *  - `clearedAt[me] = now` cuts my message view off at this instant, so the
     *    thread reads as empty for me even though the documents still exist for
     *    them.
     *
     * Nothing is destroyed, so the other person is never silently stripped of a
     * conversation they were part of — the failure mode the earlier
     * delete-for-both version had. Messaging this person again clears
     * `hiddenFor` (see [sendMessage]) and the thread comes back, showing only
     * what's arrived since the cutoff — again exactly like Messenger.
     */
    suspend fun deleteHistory(conversationId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("You're not signed in."))
        val conversationRef = firestore.collection(CONVERSATIONS).document(conversationId)

        return try {
            conversationRef.set(
                mapOf(
                    "hiddenFor" to mapOf(uid to true),
                    "clearedAt" to mapOf(uid to Timestamp.now()),
                    "unreadCount" to mapOf(uid to 0L),
                ),
                SetOptions.merge(),
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Removes one message, and whatever it had attached.
     *
     * Only the sender may — enforced by rules too, not just here. The
     * conversation's preview line is left alone even when the deleted message
     * was the last one: recomputing it means another query, and a preview that
     * briefly outlives its message is a much smaller wrong than a thread whose
     * inbox row goes blank.
     */
    suspend fun deleteMessage(conversationId: String, messageId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("You're not signed in."))
        val messageRef = firestore.collection(CONVERSATIONS).document(conversationId)
            .collection(MESSAGES).document(messageId)

        return try {
            val snapshot = messageRef.get().await()
            if (snapshot.getString("senderId") != uid) {
                return Result.failure(Exception("You can only delete your own messages."))
            }

            val keys = listOfNotNull(
                storageKeyOf(snapshot.getString("mediaUrl")),
                storageKeyOf(snapshot.getString("thumbUrl")),
            )
            if (keys.isNotEmpty()) {
                R2MediaUploader.deleteObjects(conversationId, keys)
            }

            messageRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // Blocking
    // -------------------------------------------------------------------------

    /**
     * Blocks [otherUid], writing the marker into *both* users' block lists.
     *
     * Two copies because the two sides answer different questions and each has
     * to be readable by the person asking. `users/{me}/blocked/{them}` is what
     * greys out my own composer; `users/{them}/blockedBy/{me}` is what stops
     * their client sending, and is the one the security rule consults — a rule
     * can only read documents, so the fact has to exist somewhere the sender's
     * own write can be checked against.
     */
    suspend fun setBlocked(otherUid: String, blocked: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("You're not signed in."))
        val mine = firestore.collection(USERS).document(uid)
            .collection(BLOCKED).document(otherUid)
        val theirs = firestore.collection(USERS).document(otherUid)
            .collection(BLOCKED_BY).document(uid)

        return try {
            if (blocked) {
                val stamp = mapOf("createdAt" to Timestamp.now())
                mine.set(stamp).await()
                theirs.set(stamp).await()
            } else {
                mine.delete().await()
                theirs.delete().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Live block state between the signed-in user and [otherUid].
     *
     * Watches only our own two subcollections, so it needs no permission on the
     * other account's private data: whether *they* blocked *us* is mirrored into
     * our `blockedBy` list at block time precisely so this stays readable.
     */
    fun observeBlock(otherUid: String): Flow<BlockState> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null || otherUid.isBlank()) {
            trySend(BlockState())
            awaitClose { }
            return@callbackFlow
        }

        var iBlocked = false
        var theyBlocked = false

        val mine = firestore.collection(USERS).document(uid)
            .collection(BLOCKED).document(otherUid)
            .addSnapshotListener { snap, _ ->
                iBlocked = snap?.exists() == true
                trySend(BlockState(iBlockedThem = iBlocked, theyBlockedMe = theyBlocked))
            }
        val theirs = firestore.collection(USERS).document(uid)
            .collection(BLOCKED_BY).document(otherUid)
            .addSnapshotListener { snap, _ ->
                theyBlocked = snap?.exists() == true
                trySend(BlockState(iBlockedThem = iBlocked, theyBlockedMe = theyBlocked))
            }

        awaitClose {
            mine.remove()
            theirs.remove()
        }
    }

    /**
     * People the signed-in user can start a thread with: the accounts they
     * follow, and only those.
     *
     * Reads the `following` list live each time the picker opens, so unfollowing
     * someone removes them here with no extra bookkeeping. Followers are
     * deliberately excluded — following is one-sided, so a picker built on your
     * followers is effectively open to strangers, which is how a community's DMs
     * turn into a spam channel. This matches the @mention candidate list.
     */
    suspend fun messageableUsers(): List<ChatCandidate> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        return try {
            firestore.collection(USERS).document(uid).collection(FOLLOWING)
                .limit(PICKER_LIMIT)
                .get().await()
                .documents
                .filter { it.id != uid }
                .map { doc ->
                    ChatCandidate(
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

    /** Name and photo for one user — the chat header's fallback before a thread exists. */
    suspend fun userBrief(otherUid: String): ChatCandidate? {
        return try {
            val doc = firestore.collection(USERS).document(otherUid).get().await()
            ChatCandidate(
                uid = otherUid,
                displayName = doc.getString("displayName")?.takeIf { it.isNotBlank() }
                    ?: "Unknown",
                avatarUrl = doc.getString("avatarUrl"),
            )
        } catch (_: Exception) {
            null
        }
    }

    // -------------------------------------------------------------------------

    private fun DocumentSnapshot.toConversationOrNull(): Conversation? = try {
        toObject(Conversation::class.java)?.copy(id = id)
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val CONVERSATIONS = "conversations"
        const val MESSAGES = "messages"
        const val USERS = "users"
        const val FOLLOWING = "following"
        const val FOLLOWERS = "followers"
        const val BLOCKED = "blocked"
        const val BLOCKED_BY = "blockedBy"
        const val FIELD_PARTICIPANTS = "participants"
        const val FIELD_CREATED_AT = "createdAt"

        /** How much of a thread's tail the chat screen holds in memory. */
        const val MESSAGE_PAGE_SIZE = 100L

        /** Firestore write batches cap at 500 operations. */
        const val BATCH_LIMIT = 400

        const val PICKER_LIMIT = 200L
    }
}

/** One row in the "New message" picker. */
data class ChatCandidate(
    val uid: String,
    val displayName: String,
    val avatarUrl: String?,
)

/**
 * Who has blocked whom in one conversation.
 *
 * Both directions matter and they read differently: if I blocked them the
 * composer offers to unblock, if they blocked me it just says messages can't be
 * delivered — telling someone they've been blocked by name is not information a
 * blocked person is owed.
 */
data class BlockState(
    val iBlockedThem: Boolean = false,
    val theyBlockedMe: Boolean = false,
) {
    val canSend: Boolean get() = !iBlockedThem && !theyBlockedMe
}
