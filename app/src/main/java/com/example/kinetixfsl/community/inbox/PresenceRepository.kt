package com.example.kinetixfsl.community.inbox

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The green dot next to an avatar — who is online right now.
 *
 * This is the one thing in the app that is *not* in Firestore. Firestore has no
 * way to notice that a phone dropped off the network: a user who force-quits
 * the app or walks into a tunnel would stay "online" forever, because nothing
 * ever writes them offline. The Realtime Database has `onDisconnect()`, which
 * registers the write with the server up front and fires it when the socket
 * dies — however it dies. That single primitive is why every Firebase presence
 * system, Firestore-based or not, runs on RTDB.
 *
 * Everything here degrades to silence. The Realtime Database is a separate
 * product that has to be switched on in the Firebase console (see
 * web/MESSAGING_SETUP.md); until it is, `FirebaseDatabase.getInstance()`
 * throws because google-services.json carries no database URL. Presence is a
 * nicety, so a missing database means no dots — never a crash, and never a
 * blocked message.
 */
object PresenceRepository {

    private const val PRESENCE = "presence"

    /** Null when the Realtime Database isn't configured for this project yet. */
    private val database: FirebaseDatabase? by lazy {
        runCatching { FirebaseDatabase.getInstance() }.getOrNull()
    }

    /**
     * Marks the signed-in user online, and pre-registers the write that marks
     * them offline the moment the connection drops.
     *
     * Called from `MainActivity.onResume`, and safe to call repeatedly — each
     * call just re-arms the same disconnect handler.
     */
    fun goOnline() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = database ?: return
        runCatching {
            val ref = db.getReference("$PRESENCE/$uid")

            // Arm the disconnect write *before* claiming to be online. The other
            // order leaves a window where a connection that dies mid-setup
            // leaves the user online with nothing queued to take it back.
            ref.onDisconnect().setValue(
                mapOf(
                    "state" to "offline",
                    "lastChanged" to ServerValue.TIMESTAMP,
                )
            )
            ref.setValue(
                mapOf(
                    "state" to "online",
                    "lastChanged" to ServerValue.TIMESTAMP,
                )
            )
        }
    }

    /** Explicit offline, for backgrounding the app or signing out. */
    fun goOffline() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = database ?: return
        runCatching {
            db.getReference("$PRESENCE/$uid").setValue(
                mapOf(
                    "state" to "offline",
                    "lastChanged" to ServerValue.TIMESTAMP,
                )
            )
        }
    }

    /**
     * Whether [uid] is online right now. Emits `false` and stays there when the
     * Realtime Database isn't set up, so the caller needs no special case.
     */
    fun observeOnline(uid: String): Flow<Boolean> = callbackFlow {
        val db = database
        if (db == null || uid.isBlank()) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }

        val ref = db.getReference("$PRESENCE/$uid/state")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(String::class.java) == "online")
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(false)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
