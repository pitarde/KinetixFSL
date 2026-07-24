package com.example.kinetixfsl.community.upload

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Uploads media (images and videos) to Cloudinary using their unsigned-upload
 * REST endpoint. No SDK, no API key, no server — just a multipart POST.
 *
 * HOW TO SET UP:
 * 1. Sign up at cloudinary.com (free, no card)
 * 2. Dashboard → copy your Cloud Name
 * 3. Settings → Upload → Add upload preset → name "kinetix_unsigned",
 *    signing mode "Unsigned" → Save
 * 4. Paste your cloud name into CLOUD_NAME below.
 */
object CloudinaryUploader {

    // TODO: paste your Cloudinary cloud name here (from the Dashboard).
    private const val CLOUD_NAME = "jjucg9z2"
    private const val UPLOAD_PRESET = "kinetix_unsigned"

    private const val BOUNDARY = "----KinetixUploadBoundary"

    sealed interface UploadResult {
        data class Success(val secureUrl: String) : UploadResult
        data class Error(val message: String) : UploadResult
    }

    /**
     * Uploads [uri] (an image or video picked from the gallery) to Cloudinary.
     * Returns the HTTPS URL of the uploaded file on success.
     *
     * [resourceType] must be "image" or "video". The caller picks this based on
     * the MIME type of the selected file.
     *
     * Runs on [Dispatchers.IO] — safe to call from a ViewModel coroutine.
     */
    suspend fun upload(
        context: Context,
        uri: Uri,
        resourceType: String = "image",
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            val bytes = readBytes(context, uri)
                ?: return@withContext UploadResult.Error("Could not read the selected file.")

            val fileName = uri.lastPathSegment ?: "upload"

            val endpoint = "https://api.cloudinary.com/v1_1/$CLOUD_NAME/$resourceType/upload"

            val url = URL(endpoint)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$BOUNDARY")
                connectTimeout = 30_000
                readTimeout = 120_000 // videos can be large
            }

            connection.outputStream.buffered().use { out ->
                // --- upload_preset field ---
                out.write("--$BOUNDARY\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n".toByteArray())
                out.write("$UPLOAD_PRESET\r\n".toByteArray())

                // --- file field ---
                out.write("--$BOUNDARY\r\n".toByteArray())
                out.write(
                    "Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n".toByteArray()
                )
                out.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
                out.write(bytes)
                out.write("\r\n".toByteArray())

                // --- end ---
                out.write("--$BOUNDARY--\r\n".toByteArray())
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val body = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                val secureUrl = json.getString("secure_url")
                UploadResult.Success(secureUrl)
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                UploadResult.Error("Upload failed ($responseCode): $errorBody")
            }
        } catch (e: Exception) {
            UploadResult.Error(e.localizedMessage ?: "Upload failed.")
        }
    }

    /** Reads all bytes from a content URI via the ContentResolver. */
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