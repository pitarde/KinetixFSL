package com.example.kinetixfsl.community.model

/**
 * One of the current user's comments, together with the post it sits under.
 *
 * The profile's Comments tab lists comments across every post, so each row
 * needs to say which post it was on — [postTitle] is filled in after the
 * comments arrive, since it lives on a different document.
 */
data class UserComment(
    val comment: Comment,
    val postId: String,
    val postTitle: String = "",
)
