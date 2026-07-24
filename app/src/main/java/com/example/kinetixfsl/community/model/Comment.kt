package com.example.kinetixfsl.community.model

import com.google.firebase.Timestamp

/**
 * A comment on a post. Stored in `posts/{postId}/comments/{commentId}`.
 * Author info is denormalized (same pattern as Post).
 */
data class Comment(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val body: String = "",
    val createdAt: Timestamp? = null,
)
