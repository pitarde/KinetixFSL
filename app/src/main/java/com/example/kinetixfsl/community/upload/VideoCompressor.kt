package com.example.kinetixfsl.community.upload

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Compresses a video to 480p using Media3 Transformer before uploading.
 *
 * Two things make this much faster than a naive transcode:
 *
 *  - **An explicit bitrate cap.** Without one the encoder picks a rate based on
 *    the source, so a 1080p clip re-encoded to 480p still came out at several
 *    megabits — most of the upload time was spent shipping bits nobody could
 *    see at that resolution. 1.5 Mbps is plenty for 480p.
 *  - **Skipping the transcode entirely** when the source is already small
 *    enough ([shouldSkipCompression]). Re-encoding an already-480p clip cost
 *    30+ seconds of CPU to save nothing.
 *
 * The compressed file is a temp file in the app's cache directory and should be
 * deleted after upload.
 */
object VideoCompressor {

    /** Target video height in pixels. Width scales proportionally. */
    private const val TARGET_HEIGHT = 480

    /**
     * Cap for the encoded video stream. At 480p this is visually fine and
     * roughly a third of what the encoder would otherwise choose.
     */
    private const val TARGET_BITRATE = 1_500_000

    /** Sources at or under this height are already small enough to send as-is. */
    private const val SKIP_HEIGHT_THRESHOLD = 540

    /** …as long as they're also under this size, so we don't ship a huge file. */
    private const val SKIP_SIZE_THRESHOLD_BYTES = 8L * 1024 * 1024

    /** How often to report transcoding progress. */
    private const val PROGRESS_POLL_MS = 250L

    /**
     * True when [inputUri] is already small enough that re-encoding would burn
     * time without meaningfully shrinking the upload.
     */
    fun shouldSkipCompression(context: Context, inputUri: Uri): Boolean {
        return try {
            val retriever = MediaMetadataRetriever()
            val height = try {
                retriever.setDataSource(context, inputUri)
                retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: return false
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                    // Nothing useful to do here.
                }
            }

            val sizeBytes = context.contentResolver
                .openAssetFileDescriptor(inputUri, "r")
                ?.use { it.length } ?: return false

            height <= SKIP_HEIGHT_THRESHOLD && sizeBytes in 1..SKIP_SIZE_THRESHOLD_BYTES
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Compresses the video at [inputUri] to 480p and returns the output [File].
     *
     * [onProgress] receives 0..100 as transcoding proceeds.
     *
     * Wraps the callback-based Transformer API and runs the transcoder on its
     * own HandlerThread so it doesn't block the calling coroutine's thread.
     *
     * @throws ExportException if transcoding fails.
     */
    @OptIn(UnstableApi::class)
    suspend fun compress(
        context: Context,
        inputUri: Uri,
        onProgress: (Int) -> Unit = {},
    ): File {
        val outputFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.mp4")

        return suspendCancellableCoroutine { continuation ->
            // Transformer needs a thread with a Looper.
            val thread = HandlerThread("VideoCompressor").apply { start() }
            val handler = Handler(thread.looper)

            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(TARGET_BITRATE)
                        .build()
                )
                .build()

            val transformer = Transformer.Builder(context)
                .setLooper(thread.looper)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setEncoderFactory(encoderFactory)
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

            // getProgress must be called on the Transformer's own looper.
            val progressHolder = ProgressHolder()
            var polling = true
            val poll = object : Runnable {
                override fun run() {
                    if (!polling) return
                    if (transformer.getProgress(progressHolder) !=
                        Transformer.PROGRESS_STATE_NOT_STARTED
                    ) {
                        onProgress(progressHolder.progress.coerceIn(0, 100))
                    }
                    handler.postDelayed(this, PROGRESS_POLL_MS)
                }
            }

            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    polling = false
                    onProgress(100)
                    thread.quit()
                    continuation.resume(outputFile)
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exception: ExportException,
                ) {
                    polling = false
                    thread.quit()
                    outputFile.delete()
                    continuation.resumeWithException(exception)
                }
            })

            transformer.start(editedItem, outputFile.absolutePath)
            handler.postDelayed(poll, PROGRESS_POLL_MS)

            // If the coroutine is cancelled, stop the transcoder.
            continuation.invokeOnCancellation {
                polling = false
                transformer.cancel()
                thread.quit()
                outputFile.delete()
            }
        }
    }
}
