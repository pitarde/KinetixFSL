package com.example.kinetixfsl.community.model

import com.google.firebase.Timestamp

/**
 * One community, matching the Firestore `communities/{communityId}` document.
 *
 * A community is created through the "Start a community" wizard: the creator
 * picks one or more [categories] (used by Discover to file the community under
 * each), then names and describes it. The creator becomes its admin — the only
 * one allowed to manage the category list from the community's home screen.
 *
 * Default values are here for Firestore's `toObject`, which needs a no-arg
 * constructor.
 */
data class Community(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    /**
     * The topics this community is filed under. Chosen on the first wizard
     * screen and editable later by the admin. Discover filters on these.
     */
    val categories: List<String> = emptyList(),
    val creatorId: String = "",
    val creatorName: String = "",
    val creatorAvatarUrl: String? = null,
    /** Shown under the name on the home screen. Placeholder until we track it. */
    val contributionsPerWeek: Long = 0,
    val memberCount: Long = 0,
    val createdAt: Timestamp? = null,
) {
    /** The first line of the description, for the collapsed "See more.." header. */
    val descriptionFirstLine: String
        get() = description.lineSequence().firstOrNull()?.trim().orEmpty()
}

/**
 * The fixed catalogue of topics a community can be filed under. Order and
 * wording match the first screen of the "Start a community" wizard.
 *
 * The label doubles as the stored identifier — there's no separate id, so the
 * strings written to Firestore are exactly these.
 */
object CommunityCategories {
    val ALL: List<String> = listOf(
        "FSL Alphabet",
        "Phrases & Greetings",
        "Numbers & Counting",
        "FSL Grammar & Sentence",
        "Beginner's Corner",
        "Advanced Signers",
        "Intermediate Practice",
        "Gesture Recognition",
        "Features Request",
        "Deaf Culture in the Philippines",
        "Interpreters & Educators",
        "Family & Caregivers",
        "Workplace & Inclusion",
        "Success Stories",
        "Events & Meetups",
    )
}
