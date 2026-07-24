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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: ""
        val body = intent?.getStringExtra(EXTRA_BODY) ?: ""
        val linkUrl = intent?.getStringExtra(EXTRA_LINK_URL)
        val mediaUriString = intent?.getStringExtra(EXTRA_MEDIA_URI)
        val mediaType = intent?.getStringExtra(EXTRA_MEDIA_TYPE)

        startForeground(NOTIFICATION_ID, buildUploadingNotification())

        scope.launch {
            var compressedFile: File? = null

            try {
                var imageUrl: String? = null
                var videoUrl: String? = null

                if (mediaUriString != null && mediaType != null) {
                    val mediaUri = Uri.parse(mediaUriString)

                    if (mediaType == "video") {
                        // ── Step 1: compress video to 480p ───────────────
                        updateNotification("Compressing video…")

                        compressedFile = try {
                            VideoCompressor.compress(this@PostUploadService, mediaUri)
                        } catch (e: Exception) {
                            // If compression fails, upload the original file.
                            null
                        }

                        // ── Step 2: upload the compressed (or original) video ─
                        updateNotification("Uploading video…")

                        val uploadResult = if (compressedFile != null) {
                            R2MediaUploader.uploadFile(
                                file = compressedFile,
                                resourceType = "video",
                            )
                        } else {
                            R2MediaUploader.upload(
                                context = this@PostUploadService,
                                uri = mediaUri,
                                resourceType = "video",
                            )
                        }

                        when (uploadResult) {
                            is R2MediaUploader.UploadResult.Success -> {
                                videoUrl = uploadResult.secureUrl
                            }
                            is R2MediaUploader.UploadResult.Error -> {
                                showFailedNotification(uploadResult.message)
                                stopSelf()
                                return@launch
                            }
                        }
                    } else {
                        // ── Image: already compressed inside R2MediaUploader ──
                        updateNotification("Uploading image…")

                        when (val result = R2MediaUploader.upload(
                            context = this@PostUploadService,
                            uri = mediaUri,
                            resourceType = "image",
                        )) {
                            is R2MediaUploader.UploadResult.Success -> {
                                imageUrl = result.secureUrl
                            }
                            is R2MediaUploader.UploadResult.Error -> {
                                showFailedNotification(result.message)
                                stopSelf()
                                return@launch
                            }
                        }
                    }
                }

                // ── Step 3: write the post to Firestore ──────────────────
                updateNotification("Saving post…")

                val result = repository.createPost(
                    title = title,
                    body = body,
                    linkUrl = linkUrl,
                    imageUrl = imageUrl,
                    videoUrl = videoUrl,
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
                // Clean up the temp compressed file.
                compressedFile?.delete()
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

    private fun buildUploadingNotification(text: String = "Preparing…"): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("KinetixFSL")
            .setContentText(text)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildUploadingNotification(text))
    }

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

        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_LINK_URL = "link_url"
        const val EXTRA_MEDIA_URI = "media_uri"
        const val EXTRA_MEDIA_TYPE = "media_type"
    }
}