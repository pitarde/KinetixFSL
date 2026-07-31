package com.example.kinetixfsl.community

/**
 * The public URLs a shared post lives at.
 *
 * [HOST] is the Cloudflare Worker that already handles media uploads; the
 * `/p/{postId}` route on it serves a small web page for people who don't have
 * the app, and the same URL deep-links straight into the app for people who do
 * (see the intent-filter on MainActivity in AndroidManifest.xml).
 *
 * Change [HOST] here and in the manifest together if the domain ever moves.
 */
object ShareLinks {

    const val HOST = "kinetix-upload.pitardeken2024.workers.dev"

    const val POST_PATH_PREFIX = "/p/"

    /**
     * Where media is served from. The Worker streams bucket objects under this
     * path — see [mediaUrl] for why the bucket's own r2.dev hostname isn't used.
     */
    const val MEDIA_BASE = "https://$HOST/f/"

    /** The link that goes out when a user shares a post. */
    fun postUrl(postId: String): String = "https://$HOST$POST_PATH_PREFIX$postId"

    /**
     * Pulls the post id back out of an incoming link. Handles both the https
     * form and the `kinetix://post/{id}` scheme, and returns null for anything
     * that isn't a post link.
     */
    fun postIdFrom(uri: android.net.Uri?): String? {
        if (uri == null) return null
        return when {
            uri.scheme == "kinetix" && uri.host == "post" ->
                uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }

            uri.host == HOST && uri.path?.startsWith(POST_PATH_PREFIX) == true ->
                uri.lastPathSegment?.takeIf { it.isNotBlank() }

            else -> null
        }
    }
}
