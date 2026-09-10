package com.example.kinetixfsl.community.inbox

import com.example.kinetixfsl.account.AuthoredManifest
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
 * Reads and writes a learner's notification inbox — the list behind the
 * Notification tab.
 *
 * ## Path migration (Phase 4 of FIRESTORE_RESTRUCTURE.md)
 *
 * The inbox is moving from the root collection `notifications/{uid}/items` to a
 * subcollection of the profile, `users/{uid}/notifications`, so a single
 * recursive delete of `users/{uid}` clears it. During the migration window this
 * class:
 *   • WRITES new rows to the new nested path only ([usersNotif]);
 *   • READS from BOTH paths and merges, so rows written by older clients (still
 *     under the old path) and any not-yet-migrated rows still appear;
 *   • MARK-READ / DELETE / CLEAR act on both paths;
 *   • propagate-rename queries both collection groups.
 * Phase 5 removes the old-path halves — every use is tagged `OLD PATH`.
 *
 * ## Outgoing-notification manifest (Phase 0)
 *
 * A notification is a document this user writes into *someone else's* inbox, so
 * [notify] also records an [AuthoredManifest] row under the sender's own profile.
 * That lets account deletion remove the rows a departing user left in other
 * people's inboxes — cleanup neither sweep did before.
 *
 * Every write here is best-effort. A notification that fails to land must never
 * fail the action that produced it.
 */
class NotificationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {

    // -------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------

    /**
     * The signed-in user's notifications, newest first, merged from the new
     * nested path and the old root path so nothing is missed mid-migration.
     *
     * Two live listeners feed one merged view, de-duplicated by document id
     * (a row that exists in both paths — e.g. one already backfilled — collapses
     * to a single entry). Capped at [PAGE_SIZE].
     */
    fun observeNotifications(): Flow<List<NotificationItem>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        // Latest snapshot from each source, merged on every update.
        var newRows: List<NotificationItem> = emptyList()
        var oldRows: List<NotificationItem> = emptyList()

        fun emitMerged() {
            val merged = (oldRows + newRows)
                .associateBy { it.id }        // de-dupe by id; associateBy keeps
                                              // the last, so new-path wins ties
                .values
                .sortedByDescending { it.createdAt }
                .take(PAGE_SIZE.toInt())
            trySend(merged)
        }

        fun map(snapshot: com.google.firebase.firestore.QuerySnapshot): List<NotificationItem> =
            snapshot.documents.mapNotNull { doc ->
                // Per-document, so one malformed row can't take the listener down.
                try {
                    doc.toObject(NotificationItem::class.java)?.copy(
                        id = doc.id,
                        isRead = doc.readIsRead(),
                    )
                } catch (_: Exception) {
                    null
                }
            }

        val newReg = usersNotif(uid)
            .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .limit(PAGE_SIZE)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                newRows = map(snap); emitMerged()
            }

        // OLD PATH (Phase 5: delete this listener).
        val oldReg = itemsOf(uid)
            .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .limit(PAGE_SIZE)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                oldRows = map(snap); emitMerged()
            }

        awaitClose { newReg.remove(); oldReg.remove() }
    }

    // -------------------------------------------------------------------------
    // Writing
    // -------------------------------------------------------------------------

    /**
     * Writes one notification into [recipientId]'s inbox, attributed to the
     * signed-in user, and records an outgoing-manifest row so it can be cleaned
     * up if the sender deletes their account.
     *
     * Silently does nothing when the recipient is the author or nobody is signed
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
            val ref = usersNotif(recipientId).document()
            ref.set(
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

            // Manifest row under the SENDER, pointing at the row just written —
            // so an account wipe removes the notifications this user caused in
            // other people's inboxes. Best-effort.
            runCatching {
                AuthoredManifest.refFor(firestore, me.uid, ref.path).set(
                    AuthoredManifest.entry(
                        path = ref.path,
                        type = AuthoredManifest.TYPE_NOTIFICATION,
                        parentPath = null,
                        counter = null,
                    )
                ).await()
            }
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
     * changes, a sign-in from a new device. Goes to the recipient's own inbox,
     * so it needs no manifest row (the inbox is cleared directly on deletion).
     */
    suspend fun notifySelf(message: String, recipientId: String? = null) {
        val uid = recipientId ?: auth.currentUser?.uid ?: return
        try {
            usersNotif(uid).add(
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
     * Members are read once and written in batches. No per-recipient manifest
     * row: an announcement fan-out is bounded and low-value to reverse, and
     * doubling the batch with manifest writes risks the 500-op limit. A deleted
     * poster's announcements simply age out of members' inboxes.
     */
    suspend fun notifyCommunityMembers(
        communityId: String,
        communityName: String,
        postId: String,
        message: String,
    ) {
        val me = auth.currentUser ?: return
        if (communityId.isBlank()) return

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
                        usersNotif(memberId).document(),
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
     * Flips notifications to read. The [ids] came from the merged live view, so
     * a row could live under either path — but NOT both, so each id is only
     * `update()`d, never `set(..., merge)`d.
     *
     * This distinction matters: `update()` fails (harmlessly, caught below) on a
     * document that doesn't exist, while `set(..., merge = true)` CREATES one.
     * An earlier version of this method used `set(..., merge)` on both paths
     * unconditionally, which meant marking a new-path-only row as read also
     * silently created a bogus near-empty document at the old path (same id,
     * only an `isRead` field) — a ghost the old-path listener then picked up as
     * a "new" row, merged away by id but not before firing an extra, transiently
     * inconsistent emission. That's what caused the Inbox/sidebar unread badge
     * to visibly blink. `update()` never creates a document, so no ghost, no
     * spurious emission — each id is corrected on whichever ONE path actually
     * holds it, one Firestore batch per path (a batch is atomic, so mixing a
     * doomed-to-fail update with real writes in the same batch would roll back
     * the whole thing — kept separate for exactly that reason).
     */
    suspend fun markRead(ids: List<String>) {
        val uid = auth.currentUser?.uid ?: return
        if (ids.isEmpty()) return

        ids.forEach { id ->
            runCatching { usersNotif(uid).document(id).update("isRead", true).await() }
            // OLD PATH (Phase 5: delete this line).
            runCatching { itemsOf(uid).document(id).update("isRead", true).await() }
        }
    }

    /**
     * Rewrites the sender name/photo on every notification the signed-in user
     * has ever caused, across both the new nested path and the old root path.
     *
     * The `fromUserId == me` filter is load-bearing, not a nicety: each
     * collection-group rule is written in terms of it, so a query without it is
     * rejected outright rather than returning other people's inboxes.
     */
    suspend fun propagateSenderName(displayName: String, avatarUrl: String?) {
        val uid = auth.currentUser?.uid ?: return

        suspend fun rewrite(group: String) {
            try {
                val rows = firestore.collectionGroup(group)
                    .whereEqualTo("fromUserId", uid)
                    .limit(RENAME_SCAN_LIMIT)
                    .get().await()
                    .documents
                rows.chunked(BATCH_LIMIT).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { doc ->
                        batch.set(
                            doc.reference,
                            mapOf("fromUserName" to displayName, "fromUserPhoto" to avatarUrl),
                            SetOptions.merge(),
                        )
                    }
                    batch.commit().await()
                }
            } catch (_: Exception) { /* best-effort */ }
        }

        rewrite(NOTIFICATIONS_SUB)   // new path
        rewrite(ITEMS)               // OLD PATH (Phase 5: drop this call)
    }

    /** Removes a single notification — from whichever path holds it. */
    suspend fun delete(id: String) {
        val uid = auth.currentUser?.uid ?: return
        try { usersNotif(uid).document(id).delete().await() } catch (_: Exception) { }
        // OLD PATH (Phase 5: delete this line).
        try { itemsOf(uid).document(id).delete().await() } catch (_: Exception) { }
    }

    /** Empties the whole inbox — "Clear all" — across both paths. */
    suspend fun clearAll() {
        val uid = auth.currentUser?.uid ?: return
        clearCollection(usersNotif(uid))
        // OLD PATH (Phase 5: delete this call).
        clearCollection(itemsOf(uid))
    }

    private suspend fun clearCollection(col: com.google.firebase.firestore.CollectionReference) {
        try {
            while (true) {
                val snapshot = col.limit(BATCH_LIMIT.toLong()).get().await()
                if (snapshot.isEmpty) return
                val batch = firestore.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
                if (snapshot.size() < BATCH_LIMIT) return
            }
        } catch (_: Exception) { /* best-effort */ }
    }

    // -------------------------------------------------------------------------

    /** New nested inbox — users/{uid}/notifications. */
    private fun usersNotif(uid: String) =
        firestore.collection(USERS).document(uid).collection(NOTIFICATIONS_SUB)

    /** OLD PATH — notifications/{uid}/items (Phase 5: remove). */
    private fun itemsOf(uid: String) =
        firestore.collection(NOTIFICATIONS).document(uid).collection(ITEMS)

    private companion object {
        const val USERS = "users"
        const val NOTIFICATIONS_SUB = "notifications"   // users/{uid}/notifications
        const val NOTIFICATIONS = "notifications"       // old root collection
        const val ITEMS = "items"
        const val COMMUNITIES = "communities"
        const val MEMBERS = "members"
        const val FIELD_CREATED_AT = "createdAt"

        /** How many notifications the tab holds. Recent activity, not an archive. */
        const val PAGE_SIZE = 50L

        /** Firestore write batches cap at 500 operations. */
        const val BATCH_LIMIT = 400

        /** Ceiling on how much history a rename rewrites, per path. */
        const val RENAME_SCAN_LIMIT = 500L

        /** Ceiling on an announcement fan-out. */
        const val ANNOUNCEMENT_FAN_OUT_LIMIT = 500L
    }
}
