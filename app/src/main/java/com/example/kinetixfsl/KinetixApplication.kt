package com.example.kinetixfsl

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
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
    }
}
