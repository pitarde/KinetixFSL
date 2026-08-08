package com.example.kinetixfsl.community.inbox.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.kinetixfsl.MainActivity
import com.example.kinetixfsl.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.tasks.await

/**
 * The push half of notifications — the banner that appears when the app is
 * closed or in the background.
 *
 * The in-app list is Firestore and is entirely separate (see
 * [com.example.kinetixfsl.community.inbox.NotificationRepository]). The chain
 * is: something writes a notification document → a Cloud Function fires on that
 * write → the function sends an FCM message to the recipient's device token →
 * this service turns it into a system notification.
 *
 * The client never sends a push itself. Doing that requires a server key, and a
 * server key in an APK is a key anyone can extract and use to push anything to
 * every user of the app. The Cloud Function in web/MESSAGING_SETUP.md is the
 * whole reason this indirection exists.
 */
class KinetixMessagingService : FirebaseMessagingService() {

    /**
     * FCM hands out a new token on install, on data clear, and occasionally on
     * its own. Whatever the reason, the token in Firestore has to follow it or
     * pushes start going nowhere.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmTokenStore.saveBlocking(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Two shapes arrive here. A `notification` payload is what the Cloud
        // Function sends and carries the title and body ready-made; a
        // `data`-only payload is the fallback. Reading both means the service
        // works whichever way the function is configured.
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Kinetix"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: return

        show(title, body, message.data["type"], message.data["targetId"])
    }

    private fun show(title: String, body: String, type: String?, targetId: String?) {
        // Android 13 and up won't post anything without the runtime
        // permission. Checking here rather than letting the post silently fail
        // keeps the "notification never appeared" case explainable.
        val granted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_TARGET_ID, targetId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // A per-message id, so a second notification stacks under the first
        // instead of replacing it.
        NotificationManagerCompat.from(this)
            .notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        /** Channel for social pushes. Created in `KinetixApplication`. */
        const val CHANNEL_ID = "kinetix_social"
        const val CHANNEL_NAME = "Messages and notifications"
        const val CHANNEL_IMPORTANCE = NotificationManager.IMPORTANCE_HIGH

        const val EXTRA_TYPE = "kinetix_notification_type"
        const val EXTRA_TARGET_ID = "kinetix_notification_target"
    }
}

/**
 * Keeps `users/{uid}.fcmToken` pointed at this device.
 *
 * The token is what the Cloud Function looks up to decide where to send a push,
 * so it has to be written on every sign-in — not only when FCM happens to
 * rotate it. Signing in on a second device overwrites the field, which means
 * pushes follow the most recently used device. Storing a list of tokens
 * instead is the upgrade when the app needs to reach all of a user's devices at
 * once.
 */
object FcmTokenStore {

    private const val USERS = "users"

    /**
     * Fetches the current token and stores it against the signed-in user.
     * Silent no-op when nobody is signed in — the token is stored the next time
     * this runs, which `MainActivity.onResume` guarantees.
     */
    suspend fun register() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            FirebaseFirestore.getInstance().collection(USERS).document(uid)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
                .await()
        } catch (_: Exception) { /* best-effort */ }
    }

    /**
     * The [KinetixMessagingService.onNewToken] path, which is a plain callback
     * with no coroutine scope of its own. Fire-and-forget by design: the write
     * either lands or the next `register()` picks it up.
     */
    fun saveBlocking(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        runCatching {
            FirebaseFirestore.getInstance().collection(USERS).document(uid)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
        }
    }

    /**
     * Drops the token on sign-out, so pushes meant for that account stop
     * arriving on a phone somebody else may now be holding.
     */
    fun clear() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        runCatching {
            FirebaseFirestore.getInstance().collection(USERS).document(uid)
                .set(mapOf("fcmToken" to null), SetOptions.merge())
        }
    }
}
