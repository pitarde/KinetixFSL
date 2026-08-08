package com.example.kinetixfsl

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.kinetixfsl.community.inbox.push.KinetixMessagingService
import com.example.kinetixfsl.community.upload.PostUploadService
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import java.io.File

@OptIn(UnstableApi::class)
class KinetixApplication : Application(), ImageLoaderFactory {

    companion object {
        lateinit var videoCache: SimpleCache
            private set

        /** On-disk budget for feed images. */
        private const val IMAGE_DISK_CACHE_BYTES = 200L * 1024 * 1024

        /** On-disk budget for streamed video. */
        private const val VIDEO_CACHE_BYTES = 200L * 1024 * 1024
    }

    override fun onCreate() {
        super.onCreate()

        configureFirestoreCache()

        val cacheDir = File(cacheDir, "video_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(VIDEO_CACHE_BYTES)
        videoCache = SimpleCache(cacheDir, evictor, StandaloneDatabaseProvider(this))

        // Create the notification channel for post uploads.
        createUploadNotificationChannel()

        // …and the one FCM pushes land on. Registered up front because a push
        // can arrive before any screen has been opened, and Android silently
        // drops a notification posted to a channel that doesn't exist yet.
        createSocialNotificationChannel()
    }

    /**
     * Coil's default loader honours HTTP cache headers, and our R2 objects were
     * uploaded without a `Cache-Control` header — so every image counted as
     * non-cacheable and got re-downloaded on each scroll. Unnoticeable on WiFi,
     * crippling on mobile data where every request pays a fresh handshake at
     * high latency.
     *
     * `respectCacheHeaders(false)` makes the client cache regardless, which
     * also repairs the images already sitting in the bucket without them —
     * the Worker fix only applies to new uploads.
     *
     * This is safe here because upload keys are unique: the bytes at a given
     * URL never change, so a stale cache entry is impossible.
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    /**
     * Gives Firestore an unbounded on-disk cache. Snapshot listeners then serve
     * the last known feed immediately from disk and refresh from the server
     * behind it, so opening the app on a slow connection shows posts at once
     * instead of a spinner. Must run before anything touches Firestore.
     */
    private fun configureFirestoreCache() {
        try {
            FirebaseFirestore.getInstance().firestoreSettings =
                FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                            .build()
                    )
                    .build()
        } catch (_: IllegalStateException) {
            // Already in use — settings are immutable at that point, and the
            // defaults are fine. Nothing to do.
        }
    }

    private fun createUploadNotificationChannel() {
        val channel = NotificationChannel(
            PostUploadService.CHANNEL_ID,
            "Post uploads",
            NotificationManager.IMPORTANCE_LOW,       // no sound, just the bar
        ).apply {
            description = "Shows progress when uploading a community post."
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    /**
     * Messages, replies, follows and mentions. Separate from the upload
     * channel and at a higher importance on purpose: an upload progress bar
     * should never make a sound, and a message from another person should be
     * allowed to — and the user can turn each off independently in Android's
     * own settings, which is only possible because they're two channels.
     */
    private fun createSocialNotificationChannel() {
        val channel = NotificationChannel(
            KinetixMessagingService.CHANNEL_ID,
            KinetixMessagingService.CHANNEL_NAME,
            KinetixMessagingService.CHANNEL_IMPORTANCE,
        ).apply {
            description = "New messages, replies, follows and mentions."
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}
