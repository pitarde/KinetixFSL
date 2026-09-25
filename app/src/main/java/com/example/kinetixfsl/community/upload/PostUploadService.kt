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
import com.example.kinetixfsl.community.model.storageKeyOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
        val links = intent?.getStringArrayListExtra(EXTRA_LINK_URLS).orEmpty()
        val communityId = intent?.getStringExtra(EXTRA_COMMUNITY_ID) ?: ""
        val communityName = intent?.getStringExtra(EXTRA_COMMUNITY_NAME) ?: ""
        val hashtagsText = intent?.getStringExtra(EXTRA_HASHTAGS) ?: ""
        val mediaUriStrings = intent?.getStringArrayListExtra(EXTRA_MEDIA_URIS).orEmpty()
        val mediaTypes = intent?.getStringArrayListExtra(EXTRA_MEDIA_TYPES).orEmpty()

        // Edit mode: a non-blank post id means update that post instead of
        // creating a new one. The media already on the post arrives as three
        // parallel arrays (url/type/thumb) and is kept ahead of any new uploads.
        val postId = intent?.getStringExtra(EXTRA_POST_ID).orEmpty()
        val isEdit = postId.isNotBlank()
        val existingUrls = intent?.getStringArrayListExtra(EXTRA_EXISTING_URLS).orEmpty()
        val existingTypes = intent?.getStringArrayListExtra(EXTRA_EXISTING_TYPES).orEmpty()
        val existingThumbs = intent?.getStringArrayListExtra(EXTRA_EXISTING_THUMBS).orEmpty()
        val existingMedia = existingUrls.indices.map { i ->
            PostMedia(
                url = existingUrls[i],
                type = existingTypes.getOrNull(i) ?: "image",
                thumbUrl = existingThumbs.getOrNull(i)?.takeIf { it.isNotBlank() },
            )
        }
        // The post's media as it stood before this edit — see EditPostViewModel.
        // Diffed against what's actually being kept/added so the R2 objects for
        // anything removed or replaced can be deleted, not left orphaned.
        val originalUrls = intent?.getStringArrayListExtra(EXTRA_ORIGINAL_URLS).orEmpty()
        val originalThumbs = intent?.getStringArrayListExtra(EXTRA_ORIGINAL_THUMBS).orEmpty()
        val originalPreviewUrl = intent?.getStringExtra(EXTRA_ORIGINAL_PREVIEW_URL)

        startForeground(NOTIFICATION_ID, buildUploadingNotification())

        scope.launch {
            val tempFiles = mutableListOf<File>()

            try {
                val uploaded = mutableListOf<PostMedia>()
                var previewUrl: String? = null
                var previewBlur: String? = null

                val itemCount = minOf(mediaUriStrings.size, mediaTypes.size)

                // The share-link preview/blur are only worth (re)generating for
                // whichever attachment ends up FIRST in the post overall. Final
                // order is existingMedia + uploaded, so a new upload is only the
                // true first item when there's no kept media ahead of it — if
                // existingMedia isn't empty, the post's first attachment didn't
                // change, and the existing previewUrl is still correct. Getting
                // this right matters: generating (and uploading) a preview that
                // then gets thrown away, which the code used to do for every new
                // item at loop-index 0 regardless of final position, is exactly
                // what left an untracked, unreferenced .jpg behind in R2 on every
                // such edit — untracked because it was never written to the post
                // doc, so no cleanup path (including account deletion) could ever
                // find it.
                val newItemCanBeOverallFirst = existingMedia.isEmpty()

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
                                folder = R2MediaUploader.Folder.POSTS,
                            ) { pct ->
                                updateProgress(label, scale(pct, midBand, bandEnd))
                            }
                        } else {
                            R2MediaUploader.upload(
                                context = this@PostUploadService,
                                uri = mediaUri,
                                resourceType = "video",
                                folder = R2MediaUploader.Folder.POSTS,
                            )
                        }

                        when (uploadResult) {
                            is R2MediaUploader.UploadResult.Success -> {
                                val isOverallFirst = newItemCanBeOverallFirst && index == 0
                                val derived = deriveFor(mediaUri, mediaType, isOverallFirst)
                                if (isOverallFirst) {
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
                            folder = R2MediaUploader.Folder.POSTS,
                        )) {
                            is R2MediaUploader.UploadResult.Success -> {
                                val isOverallFirst = newItemCanBeOverallFirst && index == 0
                                val derived = deriveFor(mediaUri, mediaType, isOverallFirst)
                                if (isOverallFirst) {
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

                // ── Step 2.5: free the R2 objects this edit removed ───────
                // Anything in the post's ORIGINAL media that isn't in the final
                // set (kept + newly uploaded) was removed or replaced by this
                // edit, so its files no longer belong to the post — including
                // the feed-resolution thumbUrl copy alongside the full file.
                // Must run BEFORE the Firestore write below: the Worker
                // authorises a post's media deletions against that post
                // document's CURRENTLY STORED urls (see worker.js
                // handleDeleteMedia), so the old array has to still be there
                // when this call is made — same ordering AccountEraser and the
                // admin console's account wipe already rely on.
                // The post's overall FIRST attachment is what the 1200x630
                // share-link preview (.jpg) is built from. Decide whether this
                // edit changed that first attachment — if so the stored preview
                // now shows a removed/replaced image and must be rewritten, and
                // the old .jpg deleted. (Final order is kept media, then new
                // uploads.)
                val finalMedia = existingMedia + uploaded
                val originalFirstUrl = originalUrls.firstOrNull()
                val finalFirst = finalMedia.firstOrNull()
                val previewChanged = isEdit && finalFirst?.url != originalFirstUrl

                // When the new first attachment is a NEW upload, the loop above
                // already produced its preview (previewUrl != null). When it's a
                // KEPT (already-uploaded) item — e.g. the user removed the old
                // first image and an existing one moved into first place — there
                // was no local file to derive from, so fetch that item's image
                // back from R2 and regenerate the preview here. If it can't be
                // regenerated, previewUrl stays null, which CLEARS the stale
                // preview (the Worker's share card then falls back to the full
                // first image) rather than keeping a preview of a removed image.
                if (previewChanged && previewUrl == null && finalFirst != null) {
                    updateProgress("Saving post…", 90)
                    val srcUrl = if (finalFirst.isVideo) {
                        finalFirst.thumbUrl ?: finalFirst.url
                    } else {
                        finalFirst.url
                    }
                    val bytes = runCatching { R2MediaUploader.downloadBytes(srcUrl) }.getOrNull()
                    if (bytes != null) {
                        val regen = SharePreviewGenerator.derivePreviewFromBytes(bytes)
                        previewUrl = regen.previewUrl
                        previewBlur = regen.blur
                    }
                }

                // The old preview belonged to the previous first attachment, so
                // delete it whenever the first attachment changed — whether it
                // was replaced by a new preview or cleared.
                val supersededPreviewKey = if (previewChanged) {
                    originalPreviewUrl
                        ?.takeIf { it.isNotBlank() && it != previewUrl }
                        ?.let { storageKeyOf(it) }
                } else {
                    null
                }

                if (isEdit) {
                    val keptUrls = finalMedia.map { it.url }.toSet()
                    val keptThumbs = finalMedia.mapNotNull { it.thumbUrl }.toSet()
                    val removedKeys = (
                        originalUrls.filter { it.isNotBlank() && it !in keptUrls } +
                            originalThumbs.filter { it.isNotBlank() && it !in keptThumbs }
                        )
                        .mapNotNull { storageKeyOf(it) }
                        .plus(listOfNotNull(supersededPreviewKey))
                        .distinct()
                    if (removedKeys.isNotEmpty()) {
                        runCatching { R2MediaUploader.deleteObjects(postId, removedKeys) }
                    }
                }

                // ── Step 3: write the post to Firestore ──────────────────
                updateProgress("Saving post…", 95)

                // Bounded by a timeout so the notification is ALWAYS resolved.
                // With Firestore offline persistence (on by default) a write's
                // await() completes only on SERVER acknowledgement — the write
                // commits to the local cache immediately (so the post appears in
                // the home feed at once), but on a flaky connection the ack can
                // be delayed indefinitely, leaving await() hanging and the
                // progress notification frozen mid-way even though the post is
                // effectively saved. If we don't hear back within the timeout,
                // the write is already committed locally and queued to sync, so
                // we report success rather than leaving the user staring at a
                // stuck bar. A genuine failure (returned Result) still surfaces.
                val saveResult: Result<Unit>? = withTimeoutOrNull(SAVE_TIMEOUT_MS) {
                    if (isEdit) {
                        // Retained media first, then anything just uploaded — the
                        // same order the edit screen showed. The preview fields
                        // are rewritten only when previewChanged (updatePreview);
                        // otherwise updatePost leaves the existing preview alone.
                        repository.updatePost(
                            postId = postId,
                            title = title,
                            body = body,
                            links = links,
                            media = existingMedia + uploaded,
                            communityId = communityId,
                            communityName = communityName,
                            hashtagsText = hashtagsText,
                            previewUrl = previewUrl,
                            previewBlur = previewBlur,
                            // Only rewrite the preview when the first attachment
                            // actually changed (see previewChanged above).
                            updatePreview = previewChanged,
                        )
                    } else {
                        repository.createPost(
                            title = title,
                            body = body,
                            links = links,
                            media = uploaded,
                            previewUrl = previewUrl,
                            previewBlur = previewBlur,
                            communityId = communityId,
                            communityName = communityName,
                            hashtagsText = hashtagsText,
                        ).map { }
                    }
                }

                when {
                    // Timed out waiting for the server ack — committed locally
                    // and queued; the post is already in the feed. Report done.
                    saveResult == null -> showSuccessNotification(isEdit)
                    saveResult.isSuccess -> showSuccessNotification(isEdit)
                    else -> showFailedNotification(
                        saveResult.exceptionOrNull()?.localizedMessage
                            ?: if (isEdit) "Couldn't save changes." else "Couldn't create post."
                    )
                }
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
            .setSmallIcon(R.drawable.ic_stat_kinetix)
            .setColor(BRAND_COLOR)
            .setContentTitle("KinetixFSL")
            .setOngoing(true)
            .setSilent(true)
            // Progress ticks re-post this notification many times; without this
            // some OEM shades re-animate or re-sort the row on every update,
            // which reads as the notification "flickering" or resetting.
            .setOnlyAlertOnce(true)
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

    private fun showSuccessNotification(isEdit: Boolean = false) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kinetix)
            .setColor(BRAND_COLOR)
            .setContentTitle("KinetixFSL")
            .setContentText(if (isEdit) "Post updated!" else "Post uploaded!")
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
            .setSmallIcon(R.drawable.ic_stat_kinetix)
            .setColor(BRAND_COLOR)
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

        /** Brand indigo — tints the small icon in the notification shade. */
        private const val BRAND_COLOR = 0xFF3C3489.toInt()

        /** Minimum gap between progress notifications, in ms. */
        private const val NOTIFY_THROTTLE_MS = 400L

        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_LINK_URLS = "link_urls"
        const val EXTRA_COMMUNITY_ID = "community_id"
        const val EXTRA_COMMUNITY_NAME = "community_name"
        /** Raw text of the composer's Hashtags field (its #tags are searchable). */
        const val EXTRA_HASHTAGS = "hashtags"
        const val EXTRA_MEDIA_URIS = "media_uris"
        const val EXTRA_MEDIA_TYPES = "media_types"

        /** Present (non-blank) only when editing: the post to update. */
        const val EXTRA_POST_ID = "post_id"
        /** Media already on the post, kept as-is — parallel arrays. */
        const val EXTRA_EXISTING_URLS = "existing_urls"
        const val EXTRA_EXISTING_TYPES = "existing_types"
        const val EXTRA_EXISTING_THUMBS = "existing_thumbs"
        /**
         * The post's media exactly as it was before this edit — used to work
         * out what got removed/replaced so those R2 objects can be deleted.
         * See EditPostViewModel.originalMedia.
         */
        const val EXTRA_ORIGINAL_URLS = "original_urls"
        const val EXTRA_ORIGINAL_THUMBS = "original_thumbs"
        /** The post's previewUrl before this edit — see EditPostViewModel.originalPreviewUrl. */
        const val EXTRA_ORIGINAL_PREVIEW_URL = "original_preview_url"

        /** Media uploads occupy 0..85%; preview and Firestore take the rest. */
        private const val MEDIA_BAND_END = 85

        /**
         * How long to wait for the Firestore write's SERVER acknowledgement
         * before treating the post as saved anyway. The media (the slow part)
         * is already uploaded by this point and the write is committed to the
         * local cache instantly; this only guards against a delayed server ack
         * freezing the notification. 20s is generous for a tiny document write
         * on any working connection.
         */
        private const val SAVE_TIMEOUT_MS = 20_000L
    }
}
