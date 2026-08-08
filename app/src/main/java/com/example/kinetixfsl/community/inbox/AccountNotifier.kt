package com.example.kinetixfsl.community.inbox

import android.content.Context
import android.os.Build
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * The account notices in the Inbox: the welcome when someone joins, and the
 * alert when their account signs in somewhere new.
 *
 * Runs from `MainActivity.onResume` rather than from the sign-in screens,
 * because that one call site covers every way into the app — email, Google, and
 * a session restored from a previous launch — and it's the only place that has
 * both a signed-in user and a Context.
 *
 * Every check is idempotent, so running it on each resume is free after the
 * first time: the welcome is gated on a `welcomedAt` stamp, and the sign-in
 * alert on a per-device document that already exists on the second run.
 */
object AccountNotifier {

    private const val USERS = "users"
    private const val DEVICES = "devices"
    private const val PREFS = "kinetix_device"
    private const val KEY_DEVICE_ID = "device_id"

    /**
     * Writes whichever account notices are due. Best-effort throughout — this
     * is a courtesy, and it must never be able to interfere with signing in.
     */
    suspend fun check(
        context: Context,
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
        auth: FirebaseAuth = FirebaseAuth.getInstance(),
        notifications: NotificationRepository = NotificationRepository(),
    ) {
        val user = auth.currentUser ?: return
        val userRef = firestore.collection(USERS).document(user.uid)

        // Two independent guards, not one around both. Sharing a `try` means a
        // failure in the welcome step skips the device step entirely — and the
        // device step is the one that has to run on *every* launch to record
        // this device, or a later sign-in elsewhere never looks "new".
        runCatching { checkWelcome(user, userRef, notifications) }
        runCatching { checkDevice(context, userRef, notifications) }
    }

    /** The one-time greeting, gated on a `welcomedAt` stamp. */
    private suspend fun checkWelcome(
        user: com.google.firebase.auth.FirebaseUser,
        userRef: com.google.firebase.firestore.DocumentReference,
        notifications: NotificationRepository,
    ) {
        if (userRef.get().await().get("welcomedAt") != null) return

        val name = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore('@')
            ?: "there"
        notifications.notifySelf(
            "Welcome to Kinetix, $name! Join a community and say hello."
        )
        userRef.set(mapOf("welcomedAt" to Timestamp.now()), SetOptions.merge()).await()
    }

    /**
     * Records this device and, if the account has used another one before,
     * says so.
     *
     * Note the ordering: whether this is the first device is decided *before*
     * writing this one, or the check would always find at least itself.
     */
    private suspend fun checkDevice(
        context: Context,
        userRef: com.google.firebase.firestore.DocumentReference,
        notifications: NotificationRepository,
    ) {
        val deviceId = deviceIdOf(context)
        val deviceRef = userRef.collection(DEVICES).document(deviceId)

        if (deviceRef.get().await().exists()) {
            // Known device — just refresh when it was last seen.
            deviceRef.set(mapOf("lastSignInAt" to Timestamp.now()), SetOptions.merge()).await()
            return
        }

        val label = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

        // The very first device an account signs in on is the one the account
        // was made on — alerting about that would be telling the user their own
        // sign-up was suspicious. Only the second onwards.
        val isFirstDevice = userRef.collection(DEVICES).limit(1).get().await().isEmpty

        deviceRef.set(
            mapOf(
                "label" to label,
                "lastSignInAt" to Timestamp.now(),
            )
        ).await()

        if (!isFirstDevice) {
            notifications.notifySelf(
                "Your account signed in on a new device ($label). " +
                    "If this wasn't you, change your password."
            )
        }
    }

    /**
     * A stable id for this install.
     *
     * A random UUID kept in SharedPreferences, not `ANDROID_ID` or any other
     * hardware identifier: this only has to tell "somewhere I've signed in
     * before" from "somewhere new", and a value that dies with the app's data
     * is the least the job can be done with. Reinstalling therefore reads as a
     * new device, which is the safe direction to be wrong in.
     */
    private fun deviceIdOf(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, fresh).apply()
        return fresh
    }
}
