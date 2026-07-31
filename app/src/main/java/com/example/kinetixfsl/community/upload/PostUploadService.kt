package com.example.kinetixfsl.community.upload

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.kinetixfsl.R
import com.example.kinetixfsl.community.CommunityRepository
import com.example.kinetixfsl.community.model.PostMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * A foreground service that compresses media, uploads to R2, and writes the
 * post to Firestore — all in the background while the user browses freely.
 *
 * Notification states:
 *   • "Compressing video…"   (if video attached)
 *   • "Uploading image…"     (during R2 upload)
 *   • "Uploading video…"     (during R2 upload)
 *   • "Saving post…"         (during Firestore write)
 *   • "Post uploaded!"       (auto-dismisses after 4 s)
 *   • "Upload failed"        (stays until swiped away)
 */
class PostUploadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = CommunityRepository()

    // Throttling state for the progress notification.
    private var lastPercent = -1
    private var lastText = ""
    private var lastNotifyAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: ""
        val body = intent?.getStringExtra(EXTRA_BODY) ?: ""
        val linkUrl = intent?.getStringExtra(EXTRA_LINK_URL)
        val mediaUriStrings = intent?.getStringArrayListExtra(EXTRA_MEDIA_URIS).orEmpty()
        val mediaTypes = intent?.getStringArrayListExtra(EXTRA_MEDIA_TYPES).orEmpty()

        startForeground(NOTIFICATION_ID, buildUploadingNotification())

        scope.launch {
            val tempFiles = mutableListOf<File>()

            try {
                val uploaded = mutableListOf<PostMedia>()
                var previewUrl: String? = null
                var previewBlur: String? = null

                val itemCount = minOf(mediaUriStrings.size, mediaTypes.size)

                // Each item gets an equal slice of the 0..85% band. Uploads
                // aren't equal in duration, but a bar that always advances
                // beats one that stalls on the slowest video.
                val bandPerItem = if (itemCount > 0) MEDIA_BAND_END / itemCount else 0

                for (index in 0 until itemCount) {
                    val mediaUri = Uri.parse(mediaUriStrings[index])
                    val mediaType = mediaTypes[index]
                    val bandStart = index * bandPerItem
                    val bandEnd = bandStart + bandPerItem
                    val label = if (itemCount > 1) {
                        "Uploading ${index + 1} of $itemCount…"
                    } else if (mediaType == "video") {
                        "Uploading video…"
                    } else {
                        "Uploading image…"
                    }

                    var compressedFile: File? = null

                    if (mediaType == "video") {
                        // ── Step 1: compress video to 480p ───────────────
                        // Skipped when the source is already small — saves the
                        // whole transcode, which was the bulk of the wait.
                        val skipCompression = VideoCompressor.shouldSkipCompression(
                            this@PostUploadService,
                            mediaUri,
                        )

                        // Compression takes the first half of this item's band.
                        val midBand = bandStart + (bandEnd - bandStart) / 2

                        compressedFile = if (skipCompression) {
                            null
                        } else {
                            val compressLabel = if (itemCount > 1) {
                                "Compressing ${index + 1} of $itemCount…"
                            } else {
                                "Compressing video…"
                            }
                            updateProgress(compressLabel, bandStart)
                            try {
                                VideoCompressor.compress(
                                    context = this@PostUploadService,
                                    inputUri = mediaUri,
                                ) { pct ->
                                    updateProgress(
                                        compressLabel,
                                        scale(pct, bandStart, midBand),
                                    )
                                }
                            } catch (e: Exception) {
                                // If compression fails, upload the original file.
                                null
                            }
                        }
                        compressedFile?.let { tempFiles += it }

                        // ── Step 2: upload the compressed (or original) video ─
                        updateProgress(label, midBand)

                        val uploadResult = if (compressedFile != null) {
                            R2MediaUploader.uploadFile(
                                file = compressedFile,
                                resourceType = "video",
                            ) { pct ->
                                updateProgress(label, scale(pct, midBand, bandEnd))
                            }
                        } else {
                            R2MediaUploader.upload(
                                context = this@PostUploadService,
                                uri = mediaUri,
                                resourceType = "video",
                            )
                        }

                        when (uploadResult) {
                            is R2MediaUploader.UploadResult.Success -> {
                                val derived = deriveFor(mediaUri, mediaType, index == 0)
                                if (index == 0) {
                                    previewUrl = derived.previewUrl
                                    previewBlur = derived.blur
                                }
                                uploaded += PostMedia(
                                    url = uploadResult.secureUrl,
                                    type = "video",
                                    thumbUrl = derived.feedUrl,
                                )
                            }
                            is R2MediaUploader.UploadResult.Error -> {
                                showFailedNotification(uploadResult.message)
                                stopSelf()
                                return@launch
                            }
                        }
                    } else {
                        // ── Image: already compressed inside R2MediaUploader ──
                        updateProgress(label, bandStart)

                        when (val result = R2MediaUploader.upload(
                            context = this@PostUploadService,
                            uri = mediaUri,
                            resourceType = "image",
                        )) {
                            is R2MediaUploader.UploadResult.Success -> {
                                val derived = deriveFor(mediaUri, mediaType, index == 0)
                                if (index == 0) {
                                    previewUrl = derived.previewUrl
                                    previewBlur = derived.blur
                                }
                                uploaded += PostMedia(
                                    url = result.secureUrl,
                                    type = "image",
                                    thumbUrl = derived.feedUrl,
                                )
                            }
                            is R2MediaUploader.UploadResult.Error -> {
                                showFailedNotification(result.message)
                                stopSelf()
                                return@launch
                            }
                        }
                        updateProgress(label, bandEnd)
                    }
                }

                // ── Link-preview still (1200x630) ────────────────────────
                // Built from the first attachment — that's what the carousel
                // opens on, so it's what a shared link should show. Doubles as
                // the video thumbnail. Best-effort: a null here only costs a
                // plainer card when the post gets shared, so a failure must
                // never block publishing.
                // Preview, blur and feed copies are all produced inside the loop
                // above, from a single decode per attachment.

                // ── Step 3: write the post to Firestore ──────────────────
                updateProgress("Saving post…", 95)

                val result = repository.createPost(
                    title = title,
                    body = body,
                    linkUrl = linkUrl,
                    media = uploaded,
                    previewUrl = previewUrl,
                    previewBlur = previewBlur,
                )

                result.fold(
                    onSuccess = { showSuccessNotification() },
                    onFailure = { e ->
                        showFailedNotification(
                            e.localizedMessage ?: "Couldn't create post."
                        )
                    },
                )
            } catch (e: Exception) {
                showFailedNotification(e.localizedMessage ?: "Upload failed.")
            } finally {
                // Clean up any temp compressed files.
                tempFiles.forEach { it.delete() }
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ─── Notification helpers ────────────────────────────────────────────

    /**
     * [percent] of -1 shows the old indeterminate bar; 0..100 shows a real one.
     */
    private fun buildUploadingNotification(
        text: String = "Preparing…",
        percent: Int = -1,
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("KinetixFSL")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (percent >= 0) {
            builder.setContentText("$text  $percent%")
            builder.setProgress(100, percent, false)
        } else {
            builder.setContentText(text)
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    /**
     * Pushes a progress update, rate-limited so we don't hammer the
     * NotificationManager — the upload loop reports on every percent change,
     * and Android starts dropping updates if you post too fast.
     */
    private fun updateProgress(text: String, percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        val now = System.currentTimeMillis()
        val changed = clamped != lastPercent || text != lastText

        if (!changed) return
        if (clamped < 100 && now - lastNotifyAt < NOTIFY_THROTTLE_MS) {
            // Still record it so the next tick that does get through is current.
            lastPercent = clamped
            lastText = text
            return
        }

        lastPercent = clamped
        lastText = text
        lastNotifyAt = now

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildUploadingNotification(text, clamped))
    }

    /**
     * The feed-resolution copy for one attachment. Best-effort: a null just
     * means the feed falls back to the full file, so this never fails a post.
     */
    private suspend fun deriveFor(
        uri: android.net.Uri,
        mediaType: String,
        isFirst: Boolean,
    ): SharePreviewGenerator.Derivatives = try {
        SharePreviewGenerator.derive(
            context = this,
            uri = uri,
            mediaType = mediaType,
            includeShareArtifacts = isFirst,
        )
    } catch (_: Exception) {
        SharePreviewGenerator.Derivatives()
    }

    /** Maps a 0..100 step-local percentage into an overall [from]..[to] band. */
    private fun scale(percent: Int, from: Int, to: Int): Int =
        from + (percent.coerceIn(0, 100) * (to - from)) / 100

    private fun showSuccessNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("KinetixFSL")
            .setContentText("Post uploaded!")
            .setOngoing(false)
            .setAutoCancel(true)
            .setTimeoutAfter(4_000)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        stopForeground(STOP_FOREGROUND_DETACH)
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun showFailedNotification(message: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Upload failed")
            .setContentText(message)
            .setOngoing(false)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        stopForeground(STOP_FOREGROUND_DETACH)
        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "kinetix_upload"
        const val NOTIFICATION_ID = 9001

        /** Minimum gap between progress notifications, in ms. */
        private const val NOTIFY_THROTTLE_MS = 400L

        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_LINK_URL = "link_url"
        const val EXTRA_MEDIA_URIS = "media_uris"
        const val EXTRA_MEDIA_TYPES = "media_types"

        /** Media uploads occupy 0..85%; preview and Firestore take the rest. */
        private const val MEDIA_BAND_END = 85
    }
}