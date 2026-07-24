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
import com.example.kinetixfsl.community.upload.PostUploadService
import java.io.File

@OptIn(UnstableApi::class)
class KinetixApplication : Application() {

    companion object {
        lateinit var videoCache: SimpleCache
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize ExoPlayer video cache: 100MB
        val cacheDir = File(cacheDir, "video_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024L)
        videoCache = SimpleCache(cacheDir, evictor, StandaloneDatabaseProvider(this))

        // Create the notification channel for post uploads.
        createUploadNotificationChannel()
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
}