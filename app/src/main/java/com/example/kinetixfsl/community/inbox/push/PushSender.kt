package com.example.kinetixfsl.community.inbox.push

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The push half of a notification — the banner that shows even when the app
 * is closed. Every call here follows a Firestore write into
 * `notifications/{uid}/items` ([com.example.kinetixfsl.community.inbox.NotificationRepository]
 * is the only caller); this never runs on its own.
 *
 * Sent through the Cloudflare Worker this app already runs for uploads,
 * rather than a Firebase Cloud Function. A Cloud Function needs Firebase's
 * paid Blaze plan — the free quota would cover this app's traffic, but it
 * still means a card on file. Routing through the Worker keeps Firebase on
 * the free Spark plan entirely; see web/MESSAGING_SETUP.md Part 4 for the
 * full reasoning and the Worker's `/send-push` implementation.
 *
 * The Worker never touches Firestore for this. `users/{uid}` is public read
 * (post cards need everyone's name and photo, so it already has to be), so
 * this class resolves the recipient's FCM token itself and hands the Worker a
 * token to send to, not a uid to look up. That keeps the Worker's only secret
 * to guard the Google service-account credentials FCM requires — nothing
 * about the social graph.
 */
object PushSender {

    /** Same host `R2MediaUploader` uploads to — one Worker, several jobs. */
    private const val WORKER_URL = "https://kinetix-upload.pitardeken2024.workers.dev"

    /**
     * Shared secret for the `/send-push` route. Ships inside the APK and is
     * extractable, the same caveat as `R2MediaUploader.DELETE_SECRET`. What
     * actually limits the damage: the token being pushed to is itself only
     * obtainable by reading Firestore, which anyone signed into the app can
     * already do — this secret keeps casual traffic off the endpoint, not a
     * determined attacker. See the Worker's own comment on `/send-push`.
     */
    private const val PUSH_SECRET = "kinetix-push-2026"

    /**
     * Sends a push to whoever holds [recipientId]'s account.
     *
     * Best-effort throughout: the in-app notification this always follows is
     * already the source of truth, and a push that never arrives must never
     * surface as an error to the user who triggered it. Silently does nothing
     * when the recipient has no registered token — never opened this build,
     * or signed out everywhere.
     */
    suspend fun send(
        recipientId: String,
        title: String,
        body: String,
        type: String,
        targetId: String,
    ): Unit = withContext(Dispatchers.IO) {
        try {
            val token = FirebaseFirestore.getInstance()
                .collection(USERS).document(recipientId)
                .get().await()
                .getString(FIELD_FCM_TOKEN)
                ?: return@withContext

            val payload = JSONObject().apply {
                put("token", token)
                put("title", title)
                put("body", body)
                put(
                    "data",
                    JSONObject().apply {
                        put("type", type)
                        put("targetId", targetId)
                    },
                )
            }.toString().toByteArray()

            val connection = (URL("$WORKER_URL/send-push").openConnection()
                    as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-kinetix-key", PUSH_SECRET)
                connectTimeout = 10_000
                readTimeout = 15_000
                setFixedLengthStreamingMode(payload.size)
            }
            connection.outputStream.use { it.write(payload) }
            // Draining the response is what actually flushes the request on
            // some connection-pooled setups — without touching it, an app
            // process that exits right after this call can drop the send.
            connection.responseCode
        } catch (_: Exception) { /* best-effort — see the class comment */ }
    }

    private const val USERS = "users"
    private const val FIELD_FCM_TOKEN = "fcmToken"
}
