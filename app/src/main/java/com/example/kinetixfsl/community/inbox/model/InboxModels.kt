package com.example.kinetixfsl.community.inbox.model

import com.google.firebase.Timestamp

/**
 * Everything the Inbox screen reads out of Firestore: the notification feed on
 * one tab, and direct messages on the other.
 *
 * Two separate trees, deliberately:
 *
 *   notifications/{userId}/items/{itemId}     — per-user, so a query never has
 *                                               to filter a global collection
 *   conversations/{conversationId}/messages   — per-thread, so opening a chat
 *                                               reads only that chat
 *
 * Both denormalise the other person's name and photo the same way posts and
 * comments already do, so rendering a list is one query rather than one query
 * per row.
 */

// ---------------------------------------------------------------------------
// Notifications
// ---------------------------------------------------------------------------

/**
 * What happened. Stored as the [key] string so a build that doesn't know a
 * newer type yet degrades to [SYSTEM] instead of failing to deserialize.
 */
enum class NotificationType(val key: String) {
    /** Someone sent a direct message. [NotificationItem.targetId] is the conversation. */
    MESSAGE("message"),

    /** Someone followed you. `targetId` is their uid. */
    FOLLOW("follow"),

    /** Someone upvoted your post. `targetId` is the post. */
    LIKE("like"),

    /** Someone commented on your post, or replied to your comment. `targetId` is the post. */
    COMMENT("comment"),

    /** Someone @mentioned you in a comment. `targetId` is the post. */
    MENTION("mention"),

    /** A community you joined posted something. `targetId` is the post. */
    ANNOUNCEMENT("announcement"),

    /** Account events: welcome, password changed, new device signed in. No target. */
    SYSTEM("system"),
    ;

    companion object {
        fun from(key: String?): NotificationType =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * One row in the Notification tab — `notifications/{userId}/items/{id}`.
 *
 * Every field has a default because Firestore's `toObject` needs a no-arg
 * constructor; a document written by an older build simply leaves the new
 * fields at their defaults rather than throwing.
 */
data class NotificationItem(
    val id: String = "",
    val type: String = NotificationType.SYSTEM.key,
    val fromUserId: String = "",
    val fromUserName: String = "",
    val fromUserPhoto: String? = null,
    /**
     * What tapping this opens: a post id, a conversation id, or a uid —
     * which one depends on [type]. Blank for system notifications, which
     * aren't tappable.
     */
    val targetId: String = "",
    /** The sentence shown under the name: "commented on your post". */
    val message: String = "",
    /**
     * WARNING: never populated by `toObject` — read it with
     * [readIsRead] instead. See that function for why.
     */
    val isRead: Boolean = false,
    val createdAt: Timestamp? = null,
) {
    val kind: NotificationType get() = NotificationType.from(type)
}

/**
 * Reads the `isRead` field off a document, because Firestore's object mapper
 * cannot.
 *
 * A Kotlin `Boolean` property named `isRead` compiles to a getter called
 * `isRead()` — not `getIsRead()`. Firestore's `CustomClassMapper` follows Java
 * bean convention, sees an `is` prefix, and strips it: it looks for a Firestore
 * field named **`read`**. Our documents store `isRead`, so the mapper finds
 * nothing and silently leaves the property at its default of `false`.
 *
 * Silently is the important word. Nothing throws, nothing logs — every
 * notification simply reads as unread forever, so the badge never clears no
 * matter how many times the list is opened, and a message is never "Seen".
 *
 * Reading the field by name sidesteps the mapper entirely. The writes were
 * always fine: they go through raw `hashMapOf("isRead" to ...)` maps, which
 * never touch the mapper's naming rules.
 */
internal fun com.google.firebase.firestore.DocumentSnapshot.readIsRead(): Boolean =
    getBoolean("isRead") == true

// ---------------------------------------------------------------------------
// Messaging
// ---------------------------------------------------------------------------

/**
 * One row in the Chat tab — a `conversations/{id}` document.
 *
 * [unreadCount] is a map keyed by uid rather than a single number, because each
 * participant has their own unread tally for the same thread: the sender's
 * stays at zero while the recipient's goes up. That map is what powers both the
 * blue dot on the row and the badge on the bottom-nav bell.
 */
data class Conversation(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantPhotos: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageTime: Timestamp? = null,
    val unreadCount: Map<String, Long> = emptyMap(),
    /** Who is typing right now, keyed by uid. Cleared when they stop or send. */
    val typing: Map<String, Boolean> = emptyMap(),
) {
    /** The uid of the person on the other side of this thread. */
    fun otherId(me: String): String = participants.firstOrNull { it != me }.orEmpty()

    fun otherName(me: String): String =
        participantNames[otherId(me)]?.takeIf { it.isNotBlank() } ?: "Unknown"

    fun otherPhoto(me: String): String? = participantPhotos[otherId(me)]

    fun unreadFor(me: String): Long = unreadCount[me] ?: 0L

    fun isOtherTyping(me: String): Boolean = typing[otherId(me)] == true

    /**
     * "You: Sounds good" — the preview line. Prefixed only when the last
     * message was ours, the way every messenger does it.
     */
    fun preview(me: String): String {
        val body = lastMessage.ifBlank { "" }
        return if (lastMessageSenderId == me && body.isNotBlank()) "You: $body" else body
    }
}

/** One message inside `conversations/{id}/messages`. */
data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    /** R2 URL of the single attached photo or clip, or null for a text message. */
    val mediaUrl: String? = null,
    /** "image" or "video". Null when there's no attachment. */
    val mediaType: String? = null,
    /**
     * Still frame for a video, generated and uploaded at send time.
     *
     * Without it a video bubble has nothing to draw: Coil can't decode a video
     * URL into a bitmap, so pointing it at the clip yields an empty box. Null
     * on images (which are their own preview) and on clips whose first frame
     * wouldn't decode.
     */
    val thumbUrl: String? = null,
    val createdAt: Timestamp? = null,
    /**
     * Set once the recipient opens the thread — drives the "Seen" receipt.
     *
     * Same mapper trap as [NotificationItem.isRead]: populate it with
     * [readIsRead], never from `toObject` alone.
     */
    val isRead: Boolean = false,
) {
    val isVideo: Boolean get() = mediaType == "video"
    val hasMedia: Boolean get() = !mediaUrl.isNullOrBlank()

    /** What the bubble draws: a video's still, or the image itself. */
    val previewUrl: String? get() = if (isVideo) thumbUrl else mediaUrl
}

/**
 * The document id for a one-to-one thread between [a] and [b].
 *
 * Derived from the two uids rather than random, and sorted so both sides
 * compute the same string. Without this, two people messaging each other at the
 * same moment would each create their own thread and neither would see the
 * other's messages.
 */
fun conversationIdFor(a: String, b: String): String =
    listOf(a, b).sorted().joinToString("_")
