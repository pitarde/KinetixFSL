package com.example.kinetixfsl.community

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.kinetixfsl.KinetixApplication

/**
 * Shared ExoPlayer construction for the feed, the immersive viewer and the
 * full-screen player, so buffering and caching behave identically everywhere.
 *
 * The buffer settings are tuned for mobile data rather than ExoPlayer's
 * defaults. The default waits for 2.5 s of media before it starts playing,
 * which on a high-latency connection reads as "the video is broken". Starting
 * at 1 s gets first frames up quickly, while a deeper max buffer means we grab
 * more when the connection is good so a dip doesn't stall playback.
 */
@OptIn(UnstableApi::class)
internal fun buildCachedPlayer(
    context: Context,
    /**
     * Feed players share the connection with every other feed player and with
     * image loading, so they get a deliberately shallow buffer. A full-screen
     * player is the only thing on screen and can be greedy.
     */
    feedMode: Boolean = false,
): ExoPlayer {
    // Cross-request connection reuse and sane timeouts. The default data source
    // gives up slowly on a flaky mobile link; these fail fast enough to retry.
    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(15_000)
        .setAllowCrossProtocolRedirects(true)

    val cacheDataSourceFactory = CacheDataSource.Factory()
        .setCache(KinetixApplication.videoCache)
        .setUpstreamDataSourceFactory(
            DefaultDataSource.Factory(context, httpDataSourceFactory)
        )
        // Don't fail the whole playback if the cache write errors.
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    val loadControl = if (feedMode) {
        DefaultLoadControl.Builder()
            // Shallow on purpose. A feed player that greedily buffers a minute
            // of video eats the whole connection, and two or three of them at
            // once starve image loading completely — which is what made the
            // feed crawl on mobile data while a speed test still looked fine.
            .setBufferDurationsMs(
                /* minBufferMs = */ 2_000,
                /* maxBufferMs = */ 10_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000,
            )
            // Keep the byte-based cap here: it's the thing stopping a player
            // from hoarding bandwidth.
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
    } else {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
        .setLoadControl(loadControl)
        .build()
}
