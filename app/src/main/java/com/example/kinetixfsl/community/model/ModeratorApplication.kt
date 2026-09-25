package com.example.kinetixfsl.community.model

import com.google.firebase.Timestamp

/**
 * One user's Become-a-Moderator application — `moderatorApplications/{uid}`.
 *
 * Doc id is the applicant's own uid, so there is never more than one slot per
 * user: applying again simply overwrites the previous record.
 *
 * The four stat fields are a snapshot taken at the moment of applying, so the
 * admin review queue can show them without a join back to the user's profile
 * or posts.
 */
data class ModeratorApplication(
    val uid: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val upvotesTotal: Long = 0,
    val followerCount: Long = 0,
    val postCount: Long = 0,
    val accountAgeDays: Long = 0,
    val status: String = ModeratorApplicationStatus.PENDING,
    val appliedAt: Timestamp? = null,
    val reviewedAt: Timestamp? = null,
    /** Admin uid who approved/rejected this application, or null while pending. */
    val reviewedBy: String? = null,
    val rejectionNote: String? = null,
)

/** The valid values of [ModeratorApplication.status]. */
object ModeratorApplicationStatus {
    const val PENDING = "pending"
    const val APPROVED = "approved"
    const val REJECTED = "rejected"
}
