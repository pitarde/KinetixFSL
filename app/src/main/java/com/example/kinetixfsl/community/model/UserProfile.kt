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
    /**
     * When the account was created, mirrored from Firebase Auth.
     *
     * Auth metadata is only readable for the *signed-in* user, so viewing
     * someone else's profile has no other source for their account age — it
     * has to be denormalised here at sign-in.
     */
    val createdAt: Timestamp? = null,
    /**
     * Last time this user opened the app. Drives the "Active now / 5min / 3hr /
     * 2d" indicator, same as a messenger presence dot.
     */
    val lastActiveAt: Timestamp? = null,
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
