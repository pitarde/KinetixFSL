package com.example.kinetixfsl.community.inbox

import com.example.kinetixfsl.community.inbox.model.ChatMessage
import com.example.kinetixfsl.community.inbox.model.Conversation
import com.example.kinetixfsl.community.inbox.model.NotificationType
import com.example.kinetixfsl.community.inbox.model.conversationIdFor
import com.example.kinetixfsl.community.inbox.model.readIsRead
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
     * Returns the thread with [otherUid], creating it if this is the first
     * time. Called when the Message button on a profile is tapped, so the chat
     * screen always has a document to listen to even before a word is sent.
     *
     * `merge` rather than `set`: re-opening an existing thread must refresh the
     * denormalised names and photos (people rename themselves and change their
     * avatar) without touching the last message, the unread counts, or the
     * messages underneath.
     */
    suspend fun openConversationWith(
        otherUid: String,
        otherName: String,
        otherPhoto: String?,
    ): Result<String> {
        val me = auth.currentUser ?: return Result.failure(Exception("You're not signed in."))
        if (otherUid.isBlank() || otherUid == me.uid) {
            return Result.failure(Exception("You can't message yourself."))
        }

        val id = conversationIdFor(me.uid, otherUid)
        val myName = me.displayName?.takeIf { it.isNotBlank() }
            ?: me.email?.substringBefore('@') ?: "Anonymous"

        return try {
            firestore.collection(CONVERSATIONS).document(id).set(
                mapOf(
                    "participants" to listOf(me.uid, otherUid).sorted(),
                    "participantNames" to mapOf(me.uid to myName, otherUid to otherName),
                    "participantPhotos" to mapOf(
                        me.uid to me.photoUrl?.toString(),
                        otherUid to otherPhoto,
                    ),
                ),
                SetOptions.merge(),
            ).await()
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * [openConversationWith] for a caller that has only a uid — the Message
     * button on a profile.
     *
     * Reads the name and photo from `users/{uid}` first, because the thread
     * document has to carry them: an inbox row renders straight from the
     * conversation, so a thread created without them would show a blank name
     * until the other person replied.
     */
    suspend fun openConversationWithUid(otherUid: String): Result<String> {
        return try {
            val doc = firestore.collection(USERS).document(otherUid).get().await()
            openConversationWith(
                otherUid = otherUid,
                otherName = doc.getString("displayName")?.takeIf { it.isNotBlank() } ?: "Unknown",
                otherPhoto = doc.getString("avatarUrl"),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
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

        return try {
            firestore.runTransaction { tx ->
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
                    mapOf(
                        "lastMessage" to preview,
                        "lastMessageSenderId" to me.uid,
                        "lastMessageTime" to Timestamp.now(),
                        // Only the recipient's tally moves. Ours is already
                        // zero — we're looking at the thread.
                        "unreadCount" to mapOf(recipientId to FieldValue.increment(1)),
                        // Sending ends typing, so the other side's "typing…"
                        // can't be left stuck on after the message lands.
                        "typing" to mapOf(me.uid to false),
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

    /**
     * People the signed-in user can start a thread with: everyone they follow,
     * plus everyone following them, de-duplicated.
     *
     * Deliberately not "every user in the app" — a new-message picker that
     * lists strangers is how a community app becomes a spam channel.
     */
    suspend fun messageableUsers(): List<ChatCandidate> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        val users = mutableMapOf<String, ChatCandidate>()

        suspend fun collect(path: String) {
            try {
                firestore.collection(USERS).document(uid).collection(path)
                    .limit(PICKER_LIMIT)
                    .get().await()
                    .documents.forEach { doc ->
                        if (doc.id == uid) return@forEach
                        users[doc.id] = ChatCandidate(
                            uid = doc.id,
                            displayName = doc.getString("displayName").orEmpty()
                                .ifBlank { "Unknown" },
                            avatarUrl = doc.getString("avatarUrl"),
                        )
                    }
            } catch (_: Exception) { /* best-effort */ }
        }

        collect(FOLLOWING)
        collect(FOLLOWERS)
        return users.values.sortedBy { it.displayName.lowercase() }
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
