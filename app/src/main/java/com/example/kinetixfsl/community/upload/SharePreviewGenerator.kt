package com.example.kinetixfsl.community.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Builds the image that link previews use — the picture Messenger, Facebook and
 * the rest show when someone shares a post.
 *
 * Why this exists: pointing `og:image` at the raw upload makes chat apps render
 * a card as tall as the picture, so a portrait screenshot becomes an enormous
 * card. Every platform's own links look compact because they always hand over a
 * **1.91:1 landscape** image. So we render one: center-crop the source into
 * 1200x630 and upload that alongside the real media.
 *
 * For videos this doubles as the thumbnail — there's no still to point at
 * otherwise, which is why shared videos previously got a text-only card.
 */
object SharePreviewGenerator {

    /** Facebook's recommended link-preview size, and a 1.91:1 ratio. */
    private const val PREVIEW_WIDTH = 1200
    private const val PREVIEW_HEIGHT = 630

    /** Slightly higher than the feed images — this one gets scaled up by chat apps. */
    private const val JPEG_QUALITY = 80

    /**
     * Grabs a frame this far into a video. Not frame zero: the first frame is
     * often black while the encoder settles.
     */
    private const val VIDEO_FRAME_US = 1_000_000L

    /** Longest edge of the feed-resolution copy. */
    private const val FEED_MAX_DIMENSION = 640

    /** Feed copies are scaled down on screen, so they take heavier compression. */
    private const val FEED_JPEG_QUALITY = 55

    /**
     * Longest edge of the inline blur placeholder. Deliberately tiny: this gets
     * base64'd into the Firestore document, so it must stay around a kilobyte.
     */
    private const val BLUR_MAX_DIMENSION = 24

    private const val BLUR_JPEG_QUALITY = 40

    /**
     * A feed-sized copy of the media — roughly a quarter the bytes of the full
     * upload. The feed displays media a few hundred dp tall, so shipping the
     * full-resolution file there was wasted bandwidth; the full one is still
     * used when the media opens full screen.
     */
    suspend fun generateFeedCopyAndUpload(
        context: Context,
        uri: Uri,
        mediaType: String,
    ): String? = withContext(Dispatchers.IO) {
        val source = loadSource(context, uri, mediaType) ?: return@withContext null

        val scaled = try {
            scaleToFit(source, FEED_MAX_DIMENSION)
        } finally {
            source.recycle()
        }

        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, FEED_JPEG_QUALITY, out)
            out.toByteArray()
        }
        scaled.recycle()

        when (val result = R2MediaUploader.uploadBytes(bytes, "feed.jpg", "image/jpeg")) {
            is R2MediaUploader.UploadResult.Success -> result.secureUrl
            is R2MediaUploader.UploadResult.Error -> null
        }
    }

    /**
     * A ~1 KB base64 JPEG stored directly on the post document.
     *
     * Because it rides along with the post's own Firestore read, it costs zero
     * extra network requests and paints the instant the text does. Scaled up
     * it reads as a blurred version of the picture, which is what fills the
     * media slot while the real file downloads.
     */
    suspend fun generateBlurPlaceholder(
        context: Context,
        uri: Uri,
        mediaType: String,
    ): String? = withContext(Dispatchers.IO) {
        val source = loadSource(context, uri, mediaType) ?: return@withContext null

        val tiny = try {
            scaleToFit(source, BLUR_MAX_DIMENSION)
        } finally {
            source.recycle()
        }

        val bytes = ByteArrayOutputStream().use { out ->
            tiny.compress(Bitmap.CompressFormat.JPEG, BLUR_JPEG_QUALITY, out)
            out.toByteArray()
        }
        tiny.recycle()

        try {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadSource(context: Context, uri: Uri, mediaType: String): Bitmap? =
        when (mediaType) {
            "video" -> videoFrame(context, uri)
            else -> decodeImage(context, uri)
        }

    /** Proportional downscale so the longest edge is at most [maxDimension]. */
    private fun scaleToFit(source: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxDimension) {
            return source.copy(source.config ?: Bitmap.Config.ARGB_8888, false) ?: source
        }
        val ratio = maxDimension.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    /**
     * Renders the preview for a picked image or video and uploads it.
     * Returns the public URL, or null if anything went wrong — callers should
     * treat the preview as optional and still publish the post.
     */
    suspend fun generateAndUpload(
        context: Context,
        uri: Uri,
        mediaType: String,
    ): String? = withContext(Dispatchers.IO) {
        val source = loadSource(context, uri, mediaType) ?: return@withContext null

        val preview = try {
            letterboxToPreview(source)
        } finally {
            source.recycle()
        }

        val bytes = ByteArrayOutputStream().use { out ->
            preview.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        preview.recycle()

        when (val result = R2MediaUploader.uploadBytes(bytes, "preview.jpg", "image/jpeg")) {
            is R2MediaUploader.UploadResult.Success -> result.secureUrl
            is R2MediaUploader.UploadResult.Error -> null
        }
    }

    // ─── Sources ────────────────────────────────────────────────────────

    /** Pulls a representative still out of the video. */
    private fun videoFrame(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            // Prefer a frame a second in; fall back to whatever's closest.
            retriever.getFrameAtTime(VIDEO_FRAME_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Nothing useful to do if release fails.
            }
        }
    }

    /** Decodes the picked image, downsampled so we never load a huge bitmap. */
    private fun decodeImage(context: Context, uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > PREVIEW_WIDTH * 2 ||
                bounds.outHeight / sampleSize > PREVIEW_WIDTH * 2
            ) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (_: Exception) {
            null
        }
    }

    // ─── Composition ────────────────────────────────────────────────────

    /**
     * Fits [source] inside a 1200x630 black canvas, preserving aspect ratio.
     *
     * Fit rather than crop on purpose: a tall screenshot center-cropped to
     * 1.91:1 would show a thin band from the middle and cut off whatever the
     * post was about. Letterboxing keeps the whole picture readable in the
     * preview, which is the point of the preview.
     */
    private fun letterboxToPreview(source: Bitmap): Bitmap {
        val canvasBitmap = Bitmap.createBitmap(
            PREVIEW_WIDTH,
            PREVIEW_HEIGHT,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(canvasBitmap)
        canvas.drawColor(Color.BLACK)

        val scale = minOf(
            PREVIEW_WIDTH.toFloat() / source.width,
            PREVIEW_HEIGHT.toFloat() / source.height,
        )
        val drawWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val drawHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val left = (PREVIEW_WIDTH - drawWidth) / 2
        val top = (PREVIEW_HEIGHT - drawHeight) / 2

        canvas.drawBitmap(
            source,
            null,
            Rect(left, top, left + drawWidth, top + drawHeight),
            null,
        )

        return canvasBitmap
    }
}
