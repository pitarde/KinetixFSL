package com.example.kinetixfsl.community.inbox

import com.example.kinetixfsl.community.inbox.model.NotificationItem
import com.example.kinetixfsl.community.inbox.model.NotificationType
import com.example.kinetixfsl.community.inbox.model.readIsRead
import com.example.kinetixfsl.community.inbox.push.PushSender
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Reads and writes `notifications/{userId}/items` — the list behind the
 * Notification tab.
 *
 * This is only the *in-app* half. The banner that appears when the app is
 * closed is Firebase Cloud Messaging, which a Cloud Function fires off the back
 * of the very same document write (see web/MESSAGING_SETUP.md). Nothing in this
 * file talks to FCM directly: the client writing its own pushes would mean
 * shipping a server key in the APK.
 *
 * Every write here is best-effort. A notification that fails to land must never
 * fail the action that produced it — a comment that posts but doesn't notify is
 * a small loss, a comment that refuses to post because of a notification is a
 * bug the user sees.
 */
class NotificationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {

    // -------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------

    /**
     * The signed-in user's notifications, newest first.
     *
     * Ordered on a single field, so no composite index is needed. Capped at
     * [PAGE_SIZE] — the tab is a recent-activity list, not an archive.
     */
    fun observeNotifications(): Flow<List<NotificationItem>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = itemsOf(uid)
            .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .limit(PAGE_SIZE)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                if (snapshot == null) return@addSnapshotListener
                trySend(
                    snapshot.documents.mapNotNull { doc ->
                        // Per-document, so one malformed row can't take the
                        // whole listener down — same reasoning as the feed.
                        try {
                            doc.toObject(NotificationItem::class.java)?.copy(
                                id = doc.id,
                                // Not something toObject can fill in — see readIsRead.
                                isRead = doc.readIsRead(),
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                )
            }
        awaitClose { registration.remove() }
    }

    // -------------------------------------------------------------------------
    // Writing
    // -------------------------------------------------------------------------

    /**
     * Writes one notification into [recipientId]'s list, attributed to the
     * signed-in user.
     *
     * Silently does nothing when the recipient is the author — you don't get
     * told about your own upvote on your own post — and when nobody is signed
     * in.
     */
    suspend fun notify(
        recipientId: String,
        type: NotificationType,
        targetId: String,
        message: String,
    ) {
        val me = auth.currentUser ?: return
        if (recipientId.isBlank() || recipientId == me.uid) return

        val fromName = me.displayName?.takeIf { it.isNotBlank() }
            ?: me.email?.substringBefore('@')
            ?: "Someone"

        try {
            itemsOf(recipientId).add(
                hashMapOf(
                    "type" to type.key,
                    "fromUserId" to me.uid,
                    "fromUserName" to fromName,
                    "fromUserPhoto" to me.photoUrl?.toString(),
                    "targetId" to targetId,
                    "message" to message,
                    "isRead" to false,
                    "createdAt" to Timestamp.now(),
                )
            ).await()
        } catch (_: Exception) { /* best-effort — see the class comment */ }

        // After the Firestore write, never instead of it: the in-app row is
        // the source of truth, and it must exist whether or not the device
        // that reads it is even online right now to receive a push.
        PushSender.send(
            recipientId = recipientId,
            title = fromName,
            body = message,
            type = type.key,
            targetId = targetId,
        )
    }

    /**
     * Writes a notification with no human sender — welcome messages, password
     * changes, a sign-in from a new device.
     *
     * Separate from [notify] because that one refuses to write to yourself,
     * which is exactly what these need to do.
     */
    suspend fun notifySelf(message: String, recipientId: String? = null) {
        val uid = recipientId ?: auth.currentUser?.uid ?: return
        try {
            itemsOf(uid).add(
                hashMapOf(
                    "type" to NotificationType.SYSTEM.key,
                    "fromUserId" to "",
                    "fromUserName" to "Kinetix",
                    "fromUserPhoto" to null,
                    "targetId" to "",
                    "message" to message,
                    "isRead" to false,
                    "createdAt" to Timestamp.now(),
                )
            ).await()
        } catch (_: Exception) { /* best-effort */ }

        // A welcome or new-device notice is usually written from the very
        // device that should hear about it — which just registered its own
        // token a moment earlier in the same MainActivity.onResume — so the
        // push mostly lands on the device already looking at the app. Sent
        // anyway: on a second device, or the instant after backgrounding,
        // it's the only way the notice is ever seen in real time.
        PushSender.send(
            recipientId = uid,
            title = "Kinetix",
            body = message,
            type = NotificationType.SYSTEM.key,
            targetId = "",
        )
    }

    /**
     * Sends [message] to everyone who joined [communityId] except the poster.
     *
     * Members are read once and written in batches — a community announcement
     * is a fan-out, and doing it document by document would be one round trip
     * per member.
     */
    suspend fun notifyCommunityMembers(
        communityId: String,
        communityName: String,
        postId: String,
        message: String,
    ) {
        val me = auth.currentUser ?: return
        if (communityId.isBlank()) return

        // In-app only, deliberately. [notify]'s push goes out from the poster's
        // own phone; fanning that out to every member here would mean the
        // poster's device making one Worker round trip per member on top of
        // the Firestore batches below. A community big enough for that to
        // matter is exactly the community that needs the server-side fan-out
        // called out in web/MESSAGING_SETUP.md's Part 6, not more client work.
        val fromName = communityName.ifBlank { "A community" }

        try {
            val members = firestore.collection(COMMUNITIES).document(communityId)
                .collection(MEMBERS)
                .limit(ANNOUNCEMENT_FAN_OUT_LIMIT)
                .get().await()
                .documents.map { it.id }
                .filter { it != me.uid }

            members.chunked(BATCH_LIMIT).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { memberId ->
                    batch.set(
                        itemsOf(memberId).document(),
                        hashMapOf(
                            "type" to NotificationType.ANNOUNCEMENT.key,
                            "fromUserId" to me.uid,
                            "fromUserName" to fromName,
                            "fromUserPhoto" to null,
                            "targetId" to postId,
                            "message" to message,
                            "isRead" to false,
                            "createdAt" to Timestamp.now(),
                        ),
                    )
                }
                batch.commit().await()
            }
        } catch (_: Exception) { /* best-effort */ }
    }

    // -------------------------------------------------------------------------
    // Read state
    // -------------------------------------------------------------------------

    /**
     * Flips every unread notification to read. Called when the user opens the
     * Notification tab, which is what clears the bell badge.
     *
     * Takes the ids the UI is already showing rather than re-querying: the list
     * came from a live listener a moment ago, so a second read would be paying
     * twice for the same answer.
     */
    suspend fun markRead(ids: List<String>) {
        val uid = auth.currentUser?.uid ?: return
        if (ids.isEmpty()) return
        try {
            ids.chunked(BATCH_LIMIT).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { id ->
                    batch.set(
                        itemsOf(uid).document(id),
                        mapOf("isRead" to true),
                        SetOptions.merge(),
                    )
                }
                batch.commit().await()
            }
        } catch (_: Exception) { /* best-effort */ }
    }

    /**
     * Rewrites the sender name and photo on every notification the signed-in
     * user has ever caused, in everyone else's inbox.
     *
     * Uses a collection-group query across all `items`, filtered to
     * `fromUserId == me`. That filter is not a nicety — the security rule for
     * this query is written in terms of it, so a query without it is rejected
     * outright rather than returning other people's notifications.
     *
     * Old rows are worth fixing rather than leaving: a notification list is
     * mostly history, so a rename that only affected new entries would leave a
     * user's inbox showing two different names for the same person indefinitely.
     */
    suspend fun propagateSenderName(displayName: String, avatarUrl: String?) {
        val uid = auth.currentUser?.uid ?: return
        try {
            val rows = firestore.collectionGroup(ITEMS)
                .whereEqualTo("fromUserId", uid)
                .limit(RENAME_SCAN_LIMIT)
                .get().await()
                .documents

            rows.chunked(BATCH_LIMIT).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { doc ->
                    batch.set(
                        doc.reference,
                        mapOf(
                            "fromUserName" to displayName,
                            "fromUserPhoto" to avatarUrl,
                        ),
                        SetOptions.merge(),
                    )
                }
                batch.commit().await()
            }
        } catch (_: Exception) { /* best-effort */ }
    }

    /** Removes a single notification — the swipe/long-press action on a row. */
    suspend fun delete(id: String) {
        val uid = auth.currentUser?.uid ?: return
        try {
            itemsOf(uid).document(id).delete().await()
        } catch (_: Exception) { /* best-effort */ }
    }

    /** Empties the whole list — "Clear all" in the Notification tab. */
    suspend fun clearAll() {
        val uid = auth.currentUser?.uid ?: return
        try {
            while (true) {
                val snapshot = itemsOf(uid).limit(BATCH_LIMIT.toLong()).get().await()
                if (snapshot.isEmpty) return

                val batch = firestore.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()

                if (snapshot.size() < BATCH_LIMIT) return
            }
        } catch (_: Exception) { /* best-effort */ }
    }

    // -------------------------------------------------------------------------

    private fun itemsOf(uid: String) =
        firestore.collection(NOTIFICATIONS).document(uid).collection(ITEMS)

    private companion object {
        const val NOTIFICATIONS = "notifications"
        const val ITEMS = "items"
        const val COMMUNITIES = "communities"
        const val MEMBERS = "members"
        const val FIELD_CREATED_AT = "createdAt"

        /** How many notifications the tab holds. Recent activity, not an archive. */
        const val PAGE_SIZE = 50L

        /** Firestore write batches cap at 500 operations. */
        const val BATCH_LIMIT = 400

        /**
         * Ceiling on how much history a rename rewrites. A user prolific enough
         * to exceed this has a long tail of very old notifications where a
         * stale name matters least.
         */
        const val RENAME_SCAN_LIMIT = 500L

        /**
         * Ceiling on an announcement fan-out. A community large enough to hit
         * this needs a Cloud Function doing the fan-out server-side, not a
         * phone writing a few thousand documents on the user's data plan.
         */
        const val ANNOUNCEMENT_FAN_OUT_LIMIT = 500L
    }
}
