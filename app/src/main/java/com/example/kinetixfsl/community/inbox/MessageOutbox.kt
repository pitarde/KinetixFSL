package com.example.kinetixfsl.community.inbox

import android.content.Context
import android.net.Uri
import com.example.kinetixfsl.community.upload.R2MediaUploader
import com.example.kinetixfsl.community.upload.VideoThumbnailer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A message shown in a thread before it exists in Firestore. */
data class PendingMessage(
    val localId: String,
    val conversationId: String,
    val recipientId: String,
    val text: String,
    val uri: Uri?,
    /** "image" or "video". Null for a text-only message. */
    val type: String?,
    /** Set when the upload or the write failed; the bubble offers a retry. */
    val failed: Boolean = false,
) {
    val isVideo: Boolean get() = type == "video"
}

/**
 * Messages that have been sent but haven't reached Firestore yet.
 *
 * Deliberately a process-scoped singleton rather than state inside
 * [ChatViewModel]. The ViewModel is cleared the moment the chat screen leaves
 * the composition, taking `viewModelScope` — and therefore any in-flight upload
 * — with it. That is exactly what made a video vanish when the user backed out
 * to the feed and returned, or opened a different conversation: the upload was
 * cancelled mid-flight and the only record of the message went with it.
 *
 * Living here, an upload survives navigating anywhere in the app, and the
 * pending bubble is still in the thread when the user comes back. Sends are
 * independent of each other too, so a slow video never holds up the three text
 * messages typed after it.
 *
 * Scope of the guarantee: this survives navigation, not process death. If
 * Android kills the app mid-upload the message is lost, which is why a failure
 * is surfaced in the UI as "Not sent" with a retry rather than being retried
 * invisibly. Genuine survive-anything delivery means WorkManager, and that is a
 * bigger change than this problem warrants today.
 */
object MessageOutbox {

    /**
     * SupervisorJob so one failed send can't cancel its siblings, and a scope
     * that is never cancelled — that permanence is the entire point.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repository = MessagesRepository()

    /** Pending messages by conversation id. */
    private val entries = MutableStateFlow<Map<String, List<PendingMessage>>>(emptyMap())

    /** What one chat screen renders under its real messages. */
    fun observe(conversationId: String): Flow<List<PendingMessage>> =
        entries.map { it[conversationId].orEmpty() }

    /**
     * Queues a message and starts delivering it. Returns immediately — the
     * caller's composer is free the instant this is called.
     */
    fun enqueue(
        context: Context,
        conversationId: String,
        recipientId: String,
        text: String,
        uri: Uri?,
        type: String?,
    ) {
        val entry = PendingMessage(
            localId = "local-${System.nanoTime()}",
            conversationId = conversationId,
            recipientId = recipientId,
            text = text,
            uri = uri,
            type = type,
        )
        add(entry)
        // The application context, never the Activity's: this outlives the
        // screen that started it, and holding an Activity here would leak it
        // for as long as the upload ran.
        val appContext = context.applicationContext
        scope.launch { deliver(appContext, entry) }
    }

    /** Re-runs a send the user tapped Retry on. */
    fun retry(context: Context, conversationId: String, localId: String) {
        val entry = entries.value[conversationId]?.firstOrNull { it.localId == localId } ?: return
        if (!entry.failed) return

        update(conversationId, localId) { it.copy(failed = false) }
        val appContext = context.applicationContext
        scope.launch { deliver(appContext, entry.copy(failed = false)) }
    }

    /** Drops a failed message the user has given up on. */
    fun discard(conversationId: String, localId: String) {
        entries.update { all ->
            val list = all[conversationId].orEmpty().filterNot { it.localId == localId }
            if (list.isEmpty()) all - conversationId else all + (conversationId to list)
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Uploads the attachment (and, for a video, a thumbnail) then writes the
     * message.
     *
     * On success the entry is dropped: the real message is already arriving
     * through the Firestore listener, and leaving the local copy up would show
     * it twice. On failure it stays put, marked failed — silently discarding
     * something the user wrote is the one outcome worth ruling out entirely.
     */
    private suspend fun deliver(context: Context, entry: PendingMessage) {
        var mediaUrl: String? = null
        var thumbUrl: String? = null

        val uri = entry.uri
        if (uri != null) {
            // Same R2 path posts use, under a `messages/` prefix so message
            // media can be aged out or locked down separately from public post
            // media later. No video compression pass: a chat clip is short, and
            // making someone wait on a transcode to send one is the wrong trade
            // for a message.
            when (
                val result = R2MediaUploader.upload(
                    context = context,
                    uri = uri,
                    resourceType = entry.type ?: "image",
                    folder = "messages",
                )
            ) {
                is R2MediaUploader.UploadResult.Success -> mediaUrl = result.secureUrl
                is R2MediaUploader.UploadResult.Error -> {
                    update(entry.conversationId, entry.localId) { it.copy(failed = true) }
                    return
                }
            }

            // A still for the recipient to see before pressing play. Optional
            // on purpose — a clip whose first frame won't decode should still
            // send, just without a preview.
            if (entry.isVideo) {
                thumbUrl = uploadThumbnail(context, uri)
            }
        }

        val result = repository.sendMessage(
            conversationId = entry.conversationId,
            recipientId = entry.recipientId,
            text = entry.text,
            mediaUrl = mediaUrl,
            mediaType = entry.type,
            thumbUrl = thumbUrl,
        )

        if (result.isSuccess) {
            discard(entry.conversationId, entry.localId)
        } else {
            update(entry.conversationId, entry.localId) { it.copy(failed = true) }
        }
    }

    private suspend fun uploadThumbnail(context: Context, uri: Uri): String? {
        val bytes = VideoThumbnailer.extract(context, uri) ?: return null
        return when (
            val result = R2MediaUploader.uploadBytes(
                bytes = bytes,
                fileName = "thumb.jpg",
                mimeType = "image/jpeg",
                resourceType = "image",
                folder = "messages",
            )
        ) {
            is R2MediaUploader.UploadResult.Success -> result.secureUrl
            is R2MediaUploader.UploadResult.Error -> null
        }
    }

    private fun add(entry: PendingMessage) {
        entries.update { all ->
            all + (entry.conversationId to (all[entry.conversationId].orEmpty() + entry))
        }
    }

    private fun update(
        conversationId: String,
        localId: String,
        transform: (PendingMessage) -> PendingMessage,
    ) {
        entries.update { all ->
            val list = all[conversationId].orEmpty()
                .map { if (it.localId == localId) transform(it) else it }
            all + (conversationId to list)
        }
    }
}
