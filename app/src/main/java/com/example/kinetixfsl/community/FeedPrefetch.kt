package com.example.kinetixfsl.community

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import com.example.kinetixfsl.community.model.Post
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * How many posts ahead of the viewport to warm. Small on purpose: prefetching
 * competes with the media actually on screen for the same connection, and the
 * whole reason the feed felt slow was too much downloading at once.
 */
private const val PREFETCH_AHEAD = 3

/**
 * Quietly downloads the feed-resolution image for the next few posts so they're
 * already in Coil's cache by the time the user scrolls to them — the reason
 * Facebook's feed feels like it has no loading state.
 *
 * Only images are prefetched. Video is left alone deliberately: pulling video
 * ahead of time is exactly what starved the connection before, and a video the
 * user never scrolls to is pure waste on mobile data.
 */
@Composable
internal fun FeedImagePrefetcher(
    listState: LazyListState,
    posts: List<Post>,
) {
    val context = LocalContext.current

    LaunchedEffect(listState, posts) {
        if (posts.isEmpty()) return@LaunchedEffect

        snapshotFlow {
            // Post rows are keyed by post id; the search bar and spacer aren't,
            // so keying off ids is more reliable than raw item indices.
            listState.layoutInfo.visibleItemsInfo
                .mapNotNull { it.key as? String }
                .lastOrNull()
        }
            .distinctUntilChanged()
            .collect { lastVisibleId ->
                val lastIndex = posts.indexOfFirst { it.id == lastVisibleId }
                if (lastIndex < 0) return@collect

                posts.asSequence()
                    .drop(lastIndex + 1)
                    .take(PREFETCH_AHEAD)
                    .mapNotNull { post ->
                        post.mediaItems.firstOrNull()?.takeIf { !it.isVideo }?.feedUrl
                    }
                    .forEach { url -> context.prefetchImage(url) }
            }
    }
}

/**
 * Fire-and-forget cache warm. The result is discarded — enqueuing is enough to
 * put the bytes in Coil's memory and disk caches, so the later real request
 * resolves without touching the network.
 */
private fun Context.prefetchImage(url: String) {
    imageLoader.enqueue(
        ImageRequest.Builder(this)
            .data(url)
            .build()
    )
}
