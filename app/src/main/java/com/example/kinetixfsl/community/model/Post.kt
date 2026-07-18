package com.example.kinetixfsl.community.model

import com.google.firebase.Timestamp

/**
 * One post in the community feed. Matches the Firestore `posts/{postId}` document.
 *
 * [score] = upvoteCount - downvoteCount. Maintained by the vote transaction so
 * the feed can order by it without computing at read time.
 */
data class Post(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatarUrl: String? = null,
    val title: String = "",
    val body: String = "",
    val linkUrl: String? = null,
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val upvoteCount: Long = 0,
    val downvoteCount: Long = 0,
    val commentCount: Long = 0,
    val shareCount: Long = 0,
    val viewCount: Long = 0,
    val score: Long = 0,
    val createdAt: Timestamp? = null,
)
