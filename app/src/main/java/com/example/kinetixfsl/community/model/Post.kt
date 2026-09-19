package com.example.kinetixfsl.community.model

import com.google.firebase.Timestamp

/** How many photos/videos one post may carry. */
const val MAX_POST_MEDIA = 10

/**
 * One photo or video attached to a post. [type] is "image" or "video".
 *
 * Needs a no-arg constructor for Firestore's `toObject`, which the default
 * values provide.
 */
data class PostMedia(
    /** Full-resolution file. Used when the media is opened full screen. */
    val url: String = "",
    val type: String = "image",
    /**
     * A ~640px copy, roughly a quarter the bytes. The feed shows this — it's
     * displayed a few hundred dp tall, so the full file was wasted bandwidth.
     * Null on posts created before this existed; fall back to [url].
     */
    val thumbUrl: String? = null,
) {
    val isVideo: Boolean get() = type == "video"

    /** What the feed should load: the light copy when we have one. */
    val feedUrl: String get() = thumbUrl?.takeIf { it.isNotBlank() } ?: url
}

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
    /**
     * The community this post belongs to, or blank for a Home Feed post.
     * Posts made before communities existed have no field, which deserializes
     * to "" here — so they read as Home Feed posts, exactly right.
     */
    val communityId: String = "",
    /** Denormalized community name, for showing which community a post is in. */
    val communityName: String = "",
    val title: String = "",
    val body: String = "",
    /**
     * Every #hashtag in [title]/[body], lowercased and without the leading '#'
     * — extracted and saved once at write time (see [CommunityRepository])
     * rather than parsed on every render, so the feed's search filter can
     * match a tag exactly instead of only ever substring-matching raw text.
     */
    val hashtags: List<String> = emptyList(),
    /**
     * Legacy single link. Still written (as the first of [links]) so older
     * clients and the un-redeployed web worker keep rendering something.
     * [allLinks] is what the UI should read.
     */
    val linkUrl: String? = null,
    /** Every link the author attached, in order. Empty on older posts. */
    val links: List<String> = emptyList(),
    /**
     * Legacy single-media fields. Still written for the first image/video on
     * every new post so the share page and any older client keep working, but
     * [mediaItems] is what the UI should read.
     */
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    /** All attached media, in the order the author picked them. */
    val media: List<PostMedia> = emptyList(),
    /**
     * A 1200x630 still generated at upload time, used as the link-preview image
     * when the post is shared and as the video thumbnail. Null on posts created
     * before this existed, and on text-only posts.
     */
    val previewUrl: String? = null,
    /**
     * A base64 JPEG of about a kilobyte, ~24px on its longest edge, for the
     * first attachment. It arrives with the post's own document, so it needs no
     * network request of its own and can paint the moment the text does —
     * scaled up it reads as a blurred version of the picture, filling the media
     * slot while the real file downloads.
     */
    val previewBlur: String? = null,
    val upvoteCount: Long = 0,
    val downvoteCount: Long = 0,
    val commentCount: Long = 0,
    val shareCount: Long = 0,
    val viewCount: Long = 0,
    /** How many distinct users have reported this post — one report each, see [com.example.kinetixfsl.community.ReportRepository]. */
    val reportCount: Long = 0,
    val score: Long = 0,
    val createdAt: Timestamp? = null,
    /**
     * Admin validation state: "" (not submitted), "pending" (in the admin
     * queue) or "validated" (approved). Drives the "Validated" badge on the
     * post card and the admin Content Validation queue.
     */
    val validationStatus: String = "",
    val validatedBy: String? = null,
    val validatedAt: Timestamp? = null,
) {
    /** True once an admin has approved this post. */
    val isValidated: Boolean get() = validationStatus == "validated"
    /** True while awaiting admin review. */
    val isPendingValidation: Boolean get() = validationStatus == "pending"
    /**
     * The media to display, newest scheme first. Posts made before multi-media
     * existed only have [imageUrl]/[videoUrl], so they're adapted here rather
     * than migrated — every screen can just read this.
     */
    val mediaItems: List<PostMedia>
        get() = when {
            media.isNotEmpty() -> media
            !videoUrl.isNullOrBlank() -> listOf(PostMedia(videoUrl, "video"))
            !imageUrl.isNullOrBlank() -> listOf(PostMedia(imageUrl, "image"))
            else -> emptyList()
        }

    /**
     * Every link to show, newest scheme first. Posts made before multi-link
     * existed only carry [linkUrl], so they're adapted here rather than
     * migrated — every screen reads this.
     */
    val allLinks: List<String>
        get() = links.map { it.trim() }.filter { it.isNotBlank() }
            .ifEmpty { listOfNotNull(linkUrl?.trim()?.takeIf { it.isNotBlank() }) }

    /**
     * Every file this post owns in storage, as bucket keys.
     *
     * One attachment is up to three objects — the full file, the feed copy and
     * (for the first attachment) the share preview — plus the legacy single
     * media fields on older posts. Deleting the post has to take all of them,
     * or the bucket fills with orphans nothing references.
     */
    fun storageKeys(): List<String> {
        val urls = buildList {
            add(imageUrl)
            add(videoUrl)
            add(previewUrl)
            media.forEach {
                add(it.url)
                add(it.thumbUrl)
            }
        }
        return urls.mapNotNull { storageKeyOf(it) }.distinct()
    }
}

/**
 * Turns a public media URL into its bucket key.
 * "https://host/images/123-abc.webp"   -> "images/123-abc.webp"
 * "https://host/f/images/123-abc.webp" -> "images/123-abc.webp"
 *
 * Media is served through the Worker under its media path ("/f/"), so newer
 * URLs carry an `f/` segment that is **not** part of the real R2 key — the
 * bucket only holds `images/` and `videos/`. It has to be stripped, or every
 * delete silently targets a key like `f/images/…` that doesn't exist and
 * nothing is ever freed. Older `pub-*.r2.dev/images/…` URLs have no prefix and
 * pass through unchanged. Must stay in lockstep with the Worker's `keyFromUrl`.
 */
fun storageKeyOf(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val path = runCatching { android.net.Uri.parse(url).path }
        .getOrNull()
        ?.trimStart('/')
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return path.removePrefix("f/")
}
