package com.example.kinetixfsl.community.model

import com.google.firebase.Timestamp

/**
 * One post in the community feed. Matches the Firestore `posts/{postId}` document
 * shape. Author name and avatar are denormalized snapshots so the feed can
 * render without a second fetch per post — the standard Firestore pattern.
 *
 * Counts (upvotes/downvotes/comments/shares/views) live on the post itself and
 * are updated by transactions when someone votes/comments/shares. Views can be
 * approximate; the others should stay accurate.
 *
 * Default values mean Firestore can deserialize documents that are missing
 * fields — useful when the schema evolves.
 */
data class Post(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatarUrl: String? = null,
    val title: String = "",
    val body: String = "",
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val upvoteCount: Long = 0,
    val downvoteCount: Long = 0,
    val commentCount: Long = 0,
    val shareCount: Long = 0,
    val viewCount: Long = 0,
    val createdAt: Timestamp? = null,
)