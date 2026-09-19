package com.example.kinetixfsl.community.inbox

import android.util.Log
import com.example.kinetixfsl.community.inbox.model.ChatMessage
import com.example.kinetixfsl.community.inbox.model.resolvedFor
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
        val viewerUid = auth.currentUser?.uid
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
                            val message = doc.toObject(ChatMessage::class.java)?.copy(
                                id = doc.id,
                                // Not something toObject can fill in — see readIsRead.
                                isRead = doc.readIsRead(),
                            ) ?: return@mapNotNull null
                            // Resolve any attachment to *my* own R2 copy, not
                            // necessarily the sender's — see ChatMessage.resolvedFor.
                            if (viewerUid != null) message.resolvedFor(viewerUid) else message
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
        /** See [ChatMessage.mediaDuplicated]. */
        mediaDuplicated: Boolean = false,
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
                        "mediaDuplicated" to mediaDuplicated,
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
     * Alongside that, this frees *my own* R2 copy of every photo and clip in
     * the thread — what I sent, and my duplicate of what they sent me (see
     * [freeOwnMediaCopies]) — immediately and unconditionally. That's safe
     * because each participant holds an independent copy (see
     * [ChatMessage.mediaDuplicated]): deleting mine can never break playback
     * for them, the same way it never could on Messenger. The message
     * documents themselves, and the other participant's copies, are untouched
     * — so the other person is never silently stripped of a conversation they
     * were part of. Messaging this person again clears `hiddenFor` (see
     * [sendMessage]) and the thread comes back, showing only what's arrived
     * since the cutoff.
     *
     * The message documents and thread document are torn down separately,
     * only once *nobody* can see them any more — see [freeThreadIfAbandoned].
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

            // My own copy of this conversation's media, gone right away —
            // never blocks the delete itself.
            runCatching { freeOwnMediaCopies(conversationId, uid) }

            // Separately: whether that was the second and last person to
            // leave, in which case the Firestore records themselves come down.
            runCatching { freeThreadIfAbandoned(conversationId, uid) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Frees [uid]'s own R2 copy of every photo/clip in [conversationId] —
     * what they sent, and their duplicate of whatever the other participant
     * sent them (see [ChatMessage.mediaDuplicated]) — without touching a
     * single message document or the other participant's copies.
     *
     * Safe to call unconditionally on every conversation delete: because each
     * side holds an independent R2 copy, freeing yours can never affect what
     * the other person can still play. A message from before duplication
     * shipped only ever had the sender's single copy, so this only acts on
     * that message when [uid] *is* the sender.
     */
    private suspend fun freeOwnMediaCopies(conversationId: String, uid: String) {
        val messagesRef = firestore.collection(CONVERSATIONS).document(conversationId).collection(MESSAGES)
        val keys = mutableSetOf<String>()
        var last: DocumentSnapshot? = null
        while (true) {
            var query = messagesRef.orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING).limit(BATCH_LIMIT.toLong())
            if (last != null) query = query.startAfter(last)
            val page = query.get().await()
            if (page.isEmpty) break

            page.documents.forEach { m ->
                val senderId = m.getString("senderId").orEmpty()
                val duplicated = m.getBoolean("mediaDuplicated") == true
                ownMediaKey(uid, senderId, duplicated, m.getString("mediaUrl"))?.let { keys.add(it) }
                ownMediaKey(uid, senderId, duplicated, m.getString("thumbUrl"))?.let { keys.add(it) }
            }
            if (page.size() < BATCH_LIMIT) break
            last = page.documents.last()
        }
        if (keys.isNotEmpty()) {
            runCatching { R2MediaUploader.deleteConversationObjects(conversationId, keys.toList()) }
        }
    }

    /**
     * The R2 key for [uid]'s own copy of one message attachment, or null if
     * [uid] never had a copy of it — they neither sent it nor received a
     * duplicate (an older, un-duplicated message sent by the other person).
     */
    private fun ownMediaKey(uid: String, senderId: String, duplicated: Boolean, url: String?): String? {
        val key = storageKeyOf(url) ?: return null
        return when {
            uid == senderId -> key
            duplicated -> rewriteKeyOwner(key, senderId, uid)
            else -> null
        }
    }

    /** Swaps an R2 key's leading `{uid}/` segment — the key-string equivalent of [resolvedFor]. */
    private fun rewriteKeyOwner(key: String, fromUid: String, toUid: String): String {
        val segments = key.split("/").toMutableList()
        if (segments.isNotEmpty() && segments[0] == fromUid) segments[0] = toUid
        return segments.joinToString("/")
    }

    /**
     * Tears a thread all the way down — every message document, every R2
     * copy of their media (both participants', via [wipeThread]), and the
     * conversation document itself — but only once *neither* participant can
     * still see it: each side has either deleted/hidden this conversation
     * from their own side, or no longer has an account at all.
     *
     * Media is normally already gone by the time this fires — each side frees
     * their own copy immediately on their own delete (see
     * [freeOwnMediaCopies]) — so this is mainly cleaning up the now-orphaned
     * Firestore documents. It still sweeps R2 itself as a safety net.
     */
    private suspend fun freeThreadIfAbandoned(conversationId: String, actingUid: String) {
        val threadRef = firestore.collection(CONVERSATIONS).document(conversationId)
        val snap = threadRef.get().await()
        if (!snap.exists()) return

        @Suppress("UNCHECKED_CAST")
        val participants = (snap.get("participants") as? List<*>)
            ?.mapNotNull { it as? String }
            ?: emptyList()
        val otherUid = participants.firstOrNull { it != actingUid }

        val abandoned = if (otherUid == null) {
            true // No one else was ever in this thread.
        } else {
            @Suppress("UNCHECKED_CAST")
            val hiddenFor = snap.get("hiddenFor") as? Map<String, Any?>
            val otherHidden = hiddenFor?.get(otherUid) == true
            otherHidden || runCatching {
                !firestore.collection(USERS).document(otherUid).get().await().exists()
            }.getOrDefault(false)
        }

        if (abandoned) wipeThread(threadRef, conversationId)
    }

    /**
     * Unconditionally removes every message in [threadRef], every R2 copy of
     * their media — both participants' (a duplicated attachment lives under
     * each of their own folders; see [ChatMessage.mediaDuplicated]) — and the
     * thread document itself. Callers are responsible for only invoking this
     * once the thread is actually abandoned (or, for [deleteConversation],
     * once the UI has confirmed the other side is gone).
     */
    private suspend fun wipeThread(threadRef: com.google.firebase.firestore.DocumentReference, conversationId: String) {
        @Suppress("UNCHECKED_CAST")
        val participants = runCatching {
            (threadRef.get().await().get("participants") as? List<*>)?.mapNotNull { it as? String }
        }.getOrNull().orEmpty()

        val keys = mutableSetOf<String>()
        while (true) {
            val page = threadRef.collection(MESSAGES).limit(BATCH_LIMIT.toLong()).get().await()
            if (page.isEmpty) break

            page.documents.forEach { m ->
                val senderId = m.getString("senderId").orEmpty()
                val duplicated = m.getBoolean("mediaDuplicated") == true
                listOf(m.getString("mediaUrl"), m.getString("thumbUrl")).forEach { url ->
                    val key = storageKeyOf(url) ?: return@forEach
                    keys.add(key)
                    if (duplicated && senderId.isNotBlank()) {
                        participants.filter { it != senderId }.forEach { other ->
                            keys.add(rewriteKeyOwner(key, senderId, other))
                        }
                    }
                }
            }

            val batch = firestore.batch()
            page.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()

            if (page.size() < BATCH_LIMIT) break
        }
        if (keys.isNotEmpty()) {
            runCatching { R2MediaUploader.deleteConversationObjects(conversationId, keys.toList()) }
        }
        runCatching { threadRef.delete().await() }
    }

    /**
     * Called from [com.example.kinetixfsl.account.AccountEraser] while wiping
     * an account: for every thread [uid] is in, frees [uid]'s own R2 copy of
     * that thread's media right away (see [freeOwnMediaCopies] — safe
     * regardless of whether the other participant is still around, since
     * their copy is independent), then applies the same abandoned-thread rule
     * as [deleteHistory] for the Firestore records themselves: the messages
     * and the conversation document only come down once the other participant
     * is also gone or has already hidden this conversation from their side.
     *
     * While the other participant still has this thread open, its messages —
     * and their own copy of every photo and clip either side ever sent — stay
     * completely untouched. This account disappearing must not break what it
     * already sent someone who's still around to see it; the thread simply
     * keeps showing this user's messages under whatever "no longer available"
     * treatment the UI gives a departed author.
     */
    suspend fun freeChatDataOnAccountDeletion(uid: String) = runCatching {
        val threads = firestore.collection(CONVERSATIONS)
            .whereArrayContains(FIELD_PARTICIPANTS, uid)
            .get().await()

        for (thread in threads.documents) {
            // My own copy of this thread's media, gone right away — safe
            // regardless of whether the other participant is still around,
            // since their copy (if any) is independent. See freeOwnMediaCopies.
            runCatching { freeOwnMediaCopies(thread.id, uid) }

            @Suppress("UNCHECKED_CAST")
            val otherUid = (thread.get(FIELD_PARTICIPANTS) as? List<*>)
                ?.mapNotNull { it as? String }
                ?.firstOrNull { it != uid }

            val abandoned = if (otherUid == null) {
                true
            } else {
                @Suppress("UNCHECKED_CAST")
                val hiddenFor = thread.get("hiddenFor") as? Map<String, Any?>
                val otherHidden = hiddenFor?.get(otherUid) == true
                otherHidden || runCatching {
                    !firestore.collection(USERS).document(otherUid).get().await().exists()
                }.getOrDefault(false)
            }

            if (abandoned) {
                runCatching { wipeThread(thread.reference, thread.id) }
            }
        }
    }.onFailure { Log.w(TAG, "chat data cleanup failed", it) }.let { }

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

            val duplicated = snapshot.getBoolean("mediaDuplicated") == true
            // The message document is deleted outright below (it's shared, not
            // per-user), so nobody can reach it any more once this returns —
            // free the recipient's duplicate copy too, or it leaks in R2
            // forever. Only fetched when actually needed.
            val others = if (duplicated) {
                @Suppress("UNCHECKED_CAST")
                (firestore.collection(CONVERSATIONS).document(conversationId)
                    .get().await().get(FIELD_PARTICIPANTS) as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?.filter { it != uid }
                    .orEmpty()
            } else {
                emptyList()
            }

            val keys = mutableSetOf<String>()
            listOf(snapshot.getString("mediaUrl"), snapshot.getString("thumbUrl")).forEach { url ->
                val key = storageKeyOf(url) ?: return@forEach
                keys.add(key)
                others.forEach { other -> keys.add(rewriteKeyOwner(key, uid, other)) }
            }
            if (keys.isNotEmpty()) {
                // conversationId, not deleteObjects(postId=…) — chat media has
                // no owning post, and the old call 404'd on the Worker and
                // silently freed nothing.
                R2MediaUploader.deleteConversationObjects(conversationId, keys.toList())
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

    /**
     * Whether [otherUid] still has a profile document.
     *
     * Goes false when the other person has deleted their account — the chat
     * screen uses it to swap the composer for a "no longer available" notice
     * and a Delete-conversation button. A read failure is reported as `true`
     * (present): a transient error must never make a live account look gone.
     */
    fun observeUserExists(otherUid: String): Flow<Boolean> = callbackFlow {
        if (otherUid.isBlank()) {
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }
        val reg = firestore.collection(USERS).document(otherUid)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(true); return@addSnapshotListener }
                trySend(snap?.exists() == true)
            }
        awaitClose { reg.remove() }
    }

    /**
     * Removes a whole thread — every message, its chat media in R2, and the
     * conversation document itself.
     *
     * Only permitted once the OTHER participant has deleted their account (the
     * rules check `users/{them}` no longer exists), so this is the chat
     * screen's "Delete conversation" action on a thread with a departed person,
     * not a general delete-for-both. R2 first, while the message docs still tie
     * each key to this thread.
     */
    suspend fun deleteConversation(conversationId: String): Result<Unit> {
        if (auth.currentUser == null) {
            return Result.failure(Exception("You're not signed in."))
        }
        val conversationRef = firestore.collection(CONVERSATIONS).document(conversationId)
        return try {
            wipeThread(conversationRef, conversationId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Whether [conversationId] can still be opened by the signed-in user.
     *
     * False when the thread was fully deleted (no document) or the user cleared
     * it from their own side (`hiddenFor[me] == true`) and no new message has
     * since brought it back. A notification row outlives the conversation it
     * points at, so tapping an old "sent you a message" for a thread the user
     * has since deleted should say so, not open an empty screen.
     *
     * A transient read error resolves to `true` — a network blip must not make
     * a live thread look gone.
     */
    suspend fun conversationAvailable(conversationId: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            val snap = firestore.collection(CONVERSATIONS).document(conversationId).get().await()
            if (!snap.exists()) return false
            @Suppress("UNCHECKED_CAST")
            val hiddenFor = snap.get("hiddenFor") as? Map<String, Any?>
            hiddenFor?.get(uid) != true
        } catch (_: Exception) {
            true
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
        const val TAG = "MessagesRepository"

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
