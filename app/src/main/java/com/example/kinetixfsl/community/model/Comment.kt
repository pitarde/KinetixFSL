package com.example.kinetixfsl.community.model

import com.google.firebase.Timestamp

/**
 * A comment on a post. Stored in `posts/{postId}/comments/{commentId}`.
 * Author info is denormalized (same pattern as Post).
 *
 * [imageUrl] is the one optional image a commenter may attach. The composer
 * allows a single image per comment, so this is a nullable field and not a list.
 */
data class Comment(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatarUrl: String? = null,
    val body: String = "",
    val imageUrl: String? = null,
    /**
     * Id of the top-level comment this is a reply to, or null when this *is* a
     * top-level comment. Replies live in the same subcollection as their
     * parent, so one listener still streams a whole thread.
     *
     * Threads are capped at two levels: replying to a reply stores the
     * original top-level comment's id here, never the reply's own id.
     */
    val parentId: String? = null,
    /**
     * Uids the author picked from the @mention autocomplete.
     *
     * Stored alongside the text rather than parsed back out of it: a display
     * name with a space in it can't be recovered from "@Juan Dela Cruz said so"
     * without guessing where the name ends. Recording who was meant at the
     * moment the author chose them removes the guess entirely.
     */
    val mentionedUserIds: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
)
