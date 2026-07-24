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
     * Uploads a File directly (used for compressed videos from VideoCompressor).
     */
    suspend fun uploadFile(
        file: File,
        resourceType: String = "video",
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            val bytes = FileInputStream(file).use { it.readBytes() }
            val mimeType = "video/mp4"
            val fileName = file.name
            performUpload(bytes, fileName, mimeType, resourceType)
        } catch (e: Exception) {
            UploadResult.Error(e.localizedMessage ?: "Upload failed.")
        }
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

        val responseCode = connection.responseCode
        return if (responseCode in 200..299) {
            val body = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val secureUrl = json.getString("secure_url")
            UploadResult.Success(secureUrl)
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