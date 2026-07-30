package com.example.kinetixfsl.community.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Uploads media (images and videos) to Cloudflare R2 via a Cloudflare Worker.
 * Images are compressed before upload for much faster posting.
 */
object R2MediaUploader {

    private const val WORKER_URL = "https://kinetix-upload.pitardeken2024.workers.dev"

    private const val BOUNDARY = "----KinetixR2UploadBoundary"

    /** Max image dimension (width or height). Larger images get downscaled. */
    private const val MAX_IMAGE_DIMENSION = 960

    /** JPEG compression quality (0-100). 60 is good for social media. */
    private const val JPEG_QUALITY = 60

    /** Copy buffer for streamed uploads. 64 KB keeps the socket well fed. */
    private const val STREAM_BUFFER_SIZE = 64 * 1024

    sealed interface UploadResult {
        data class Success(val secureUrl: String) : UploadResult
        data class Error(val message: String) : UploadResult
    }

    /**
     * Uploads a content URI (image or video picked from gallery).
     * Images are compressed automatically before upload.
     */
    suspend fun upload(
        context: Context,
        uri: Uri,
        resourceType: String = "image",
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            val bytes: ByteArray
            val mimeType: String
            val fileName: String

            if (resourceType == "image") {
                val compressed = compressImage(context, uri)
                    ?: return@withContext UploadResult.Error("Could not read the selected image.")
                bytes = compressed
                mimeType = "image/jpeg"
                fileName = "photo.jpg"
            } else {
                bytes = readBytes(context, uri)
                    ?: return@withContext UploadResult.Error("Could not read the selected file.")
                mimeType = context.contentResolver.getType(uri) ?: "video/mp4"
                fileName = uri.lastPathSegment ?: "video.mp4"
            }

            performUpload(bytes, fileName, mimeType, resourceType)
        } catch (e: Exception) {
            UploadResult.Error(e.localizedMessage ?: "Upload failed.")
        }
    }

    /**
     * Uploads bytes we generated ourselves rather than a file the user picked —
     * currently the 1.91:1 link-preview image from [SharePreviewGenerator].
     * Already encoded, so no further compression.
     */
    suspend fun uploadBytes(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        resourceType: String = "image",
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            performUpload(bytes, fileName, mimeType, resourceType)
        } catch (e: Exception) {
            UploadResult.Error(e.localizedMessage ?: "Upload failed.")
        }
    }

    /**
     * Uploads a File directly (used for compressed videos from VideoCompressor).
     */
    suspend fun uploadFile(
        file: File,
        resourceType: String = "video",
        onProgress: (Int) -> Unit = {},
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            // Streamed rather than read into a ByteArray first: a 50 MB video
            // used to be fully materialised in memory before a single byte went
            // out, which cost time up front and risked an OOM on cheaper
            // phones. Now bytes go to the socket as they come off disk.
            streamUpload(file, "video/mp4", resourceType, onProgress)
        } catch (e: Exception) {
            UploadResult.Error(e.localizedMessage ?: "Upload failed.")
        }
    }

    /**
     * Multipart POST that streams [file] straight through to the Worker,
     * reporting 0..100 as it goes.
     *
     * Uses a fixed content length rather than chunked encoding — the total size
     * is known up front, and fixed-length lets the connection avoid the
     * per-chunk framing overhead.
     */
    private fun streamUpload(
        file: File,
        mimeType: String,
        resourceType: String,
        onProgress: (Int) -> Unit,
    ): UploadResult {
        val prefix = buildString {
            append("--$BOUNDARY\r\n")
            append("Content-Disposition: form-data; name=\"resource_type\"\r\n\r\n")
            append("$resourceType\r\n")
            append("--$BOUNDARY\r\n")
            append("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n")
            append("Content-Type: $mimeType\r\n\r\n")
        }.toByteArray()
        val suffix = "\r\n--$BOUNDARY--\r\n".toByteArray()

        val fileLength = file.length()
        val totalLength = prefix.size + fileLength + suffix.size

        val connection = (URL(WORKER_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$BOUNDARY")
            connectTimeout = 30_000
            readTimeout = 120_000
            setFixedLengthStreamingMode(totalLength)
        }

        connection.outputStream.buffered(STREAM_BUFFER_SIZE).use { out ->
            out.write(prefix)

            FileInputStream(file).use { input ->
                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                var sent = 0L
                var lastReported = -1
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    sent += read

                    if (fileLength > 0) {
                        val pct = ((sent * 100) / fileLength).toInt().coerceIn(0, 100)
                        // Only fire on change — this loop runs thousands of times.
                        if (pct != lastReported) {
                            lastReported = pct
                            onProgress(pct)
                        }
                    }
                }
            }

            out.write(suffix)
            out.flush()
        }

        return readUploadResponse(connection)
    }

    /**
     * The actual multipart POST to the Cloudflare Worker.
     */
    private fun performUpload(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        resourceType: String,
    ): UploadResult {
        val url = URL(WORKER_URL)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$BOUNDARY")
            connectTimeout = 30_000
            readTimeout = 120_000
            setChunkedStreamingMode(0)
        }

        connection.outputStream.buffered().use { out ->
            // --- resource_type field ---
            out.write("--$BOUNDARY\r\n".toByteArray())
            out.write("Content-Disposition: form-data; name=\"resource_type\"\r\n\r\n".toByteArray())
            out.write("$resourceType\r\n".toByteArray())

            // --- file field ---
            out.write("--$BOUNDARY\r\n".toByteArray())
            out.write(
                "Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n".toByteArray()
            )
            out.write("Content-Type: $mimeType\r\n\r\n".toByteArray())
            out.write(bytes)
            out.write("\r\n".toByteArray())

            // --- end ---
            out.write("--$BOUNDARY--\r\n".toByteArray())
        }

        return readUploadResponse(connection)
    }

    /** Shared response handling for both the buffered and streamed paths. */
    private fun readUploadResponse(connection: HttpURLConnection): UploadResult {
        val responseCode = connection.responseCode
        return if (responseCode in 200..299) {
            val body = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            UploadResult.Success(json.getString("secure_url"))
        } else {
            val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            UploadResult.Error("Upload failed ($responseCode): $errorBody")
        }
    }

    // ─── Image compression ──────────────────────────────────────────────

    /**
     * Decodes the image, downscales to [MAX_IMAGE_DIMENSION], and
     * re-encodes as JPEG at [JPEG_QUALITY]. A 6 MB camera photo
     * typically becomes ~80-200 KB.
     */
    private fun compressImage(context: Context, uri: Uri): ByteArray? {
        return try {
            // First pass: get dimensions without loading pixels.
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            // Calculate how much to downsample.
            var sampleSize = 1
            while (origWidth / sampleSize > MAX_IMAGE_DIMENSION * 2 ||
                origHeight / sampleSize > MAX_IMAGE_DIMENSION * 2
            ) {
                sampleSize *= 2
            }

            // Second pass: decode with sample size.
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            // Scale down if still larger than the max dimension.
            val scaled = scaleDown(bitmap, MAX_IMAGE_DIMENSION)

            // Encode to JPEG.
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)

            // Recycle bitmaps to free memory.
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()

            output.toByteArray()
        } catch (_: Exception) {
            null
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
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // ─── Raw byte reading ───────────────────────────────────────────────

    private fun readBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var read: Int
                while (input.read(chunk).also { read = it } != -1) {
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            }
        } catch (_: Exception) {
            null
        }
    }
}