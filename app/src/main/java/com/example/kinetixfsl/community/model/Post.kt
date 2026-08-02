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
    val linkUrl: String? = null,
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
    val score: Long = 0,
    val createdAt: Timestamp? = null,
) {
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
 * "https://host/images/123-abc.webp" -> "images/123-abc.webp"
 */
fun storageKeyOf(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return runCatching {
        android.net.Uri.parse(url).path?.trimStart('/')
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
