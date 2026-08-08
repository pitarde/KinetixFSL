package com.example.kinetixfsl.community.upload

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Pulls a still frame out of a video so a message can show something before the
 * clip is played.
 *
 * Needed because Coil can't decode a video URL on its own — pointed at an MP4 it
 * downloads the file and fails to produce a bitmap, which is why a video message
 * used to render as an empty grey box with a play glyph floating on it. Posts
 * solved the same problem with `previewUrl`; this is the messaging equivalent.
 *
 * The frame is grabbed locally at send time and uploaded alongside the video, so
 * the recipient gets a plain image URL and needs no video decoding at all to
 * render the bubble.
 */
object VideoThumbnailer {

    /** Longest edge of the generated still. Big enough for a 280dp bubble. */
    private const val MAX_DIMENSION = 640

    private const val JPEG_QUALITY = 70

    /**
     * A JPEG still from early in [uri], or null if the frame can't be read.
     *
     * Null is a perfectly normal outcome — some codecs and some content
     * providers simply won't yield a frame — so callers treat the thumbnail as
     * optional and fall back to the plain play glyph.
     */
    suspend fun extract(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            // One second in, not zero. The first frame of a phone recording is
            // very often black or a blur while the sensor settles, which makes
            // for a thumbnail that tells the viewer nothing.
            val frame = retriever.getFrameAtTime(
                1_000_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            ) ?: retriever.frameAtTime // fall back to whatever it will give us
            ?: return@withContext null

            val scaled = scaleDown(frame, MAX_DIMENSION)
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)

            if (scaled !== frame) scaled.recycle()
            frame.recycle()

            output.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) { /* nothing useful to do */ }
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = minOf(
            maxDimension.toFloat() / width,
            maxDimension.toFloat() / height,
        )
        return Bitmap.createScaledBitmap(
            bitmap,
            (width * ratio).toInt().coerceAtLeast(1),
            (height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }
}
