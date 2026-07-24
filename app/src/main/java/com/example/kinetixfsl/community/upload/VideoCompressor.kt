package com.example.kinetixfsl.community.upload

import android.content.Context
import android.net.Uri
import android.os.HandlerThread
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Compresses a video to 480p using Media3 Transformer before uploading.
 *
 * A 1080p, 50 MB video typically becomes ~5-8 MB at 480p, making
 * the upload 5-10x faster — similar to how Facebook/Instagram handle it.
 *
 * The compressed file is saved as a temp file in the app's cache directory
 * and should be deleted after upload.
 */
object VideoCompressor {

    /** Target video height in pixels. Width scales proportionally. */
    private const val TARGET_HEIGHT = 480

    /**
     * Compresses the video at [inputUri] to 480p and returns the output [File].
     *
     * This is a suspend function that wraps the callback-based Transformer API.
     * It runs the transcoder on its own HandlerThread so it doesn't block
     * the calling coroutine's thread.
     *
     * @throws ExportException if transcoding fails.
     */
    @OptIn(UnstableApi::class)
    suspend fun compress(context: Context, inputUri: Uri): File {
        // Create a temp file for the compressed output.
        val outputFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.mp4")

        return suspendCancellableCoroutine { continuation ->
            // Transformer needs a thread with a Looper.
            val thread = HandlerThread("VideoCompressor").apply { start() }

            val transformer = Transformer.Builder(context)
                .setLooper(thread.looper)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .build()

            val effects = Effects(
                /* audioProcessors = */ listOf(),
                /* videoEffects = */ listOf(
                    Presentation.createForHeight(TARGET_HEIGHT)
                ),
            )

            val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
                .setEffects(effects)
                .build()

            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    thread.quit()
                    continuation.resume(outputFile)
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exception: ExportException,
                ) {
                    thread.quit()
                    outputFile.delete()
                    continuation.resumeWithException(exception)
                }
            })

            transformer.start(editedItem, outputFile.absolutePath)

            // If the coroutine is cancelled, stop the transcoder.
            continuation.invokeOnCancellation {
                transformer.cancel()
                thread.quit()
                outputFile.delete()
            }
        }
    }
}