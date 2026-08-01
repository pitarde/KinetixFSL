package com.example.kinetixfsl.community.model

import com.google.firebase.Timestamp

/**
 * A user's community profile — `users/{uid}`.
 *
 * Counts are denormalised so a profile can show "120 followers" without
 * counting a subcollection on every open. They're maintained by the follow
 * transaction, which writes the relationship and the counters together.
 */
data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val followerCount: Long = 0,
    val followingCount: Long = 0,
)

/**
 * One entry in a followers or following list. Author details are copied in at
 * follow time, the same denormalisation the posts use, so rendering a list
 * needs one query rather than one query per row.
 */
data class FollowUser(
    val uid: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val createdAt: Timestamp? = null,
)
