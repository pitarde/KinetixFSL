package com.example.kinetixfsl.profile

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.FileOutputStream

/**
 * The user's editable display name and profile photo for the (offline)
 * Profile screen — stored entirely on this device: the name in
 * SharedPreferences, the photo as a private copy under the app's own files
 * directory. Neither ever touches the Firebase account; this only overrides
 * what the Profile screen shows on this phone. See [EditProfileSheet].
 *
 * Scoped per signed-in uid — both the preference key and the avatar filename
 * are namespaced by [uid]. Without this, every account sharing this device
 * read and wrote the exact same slot: switching accounts kept showing the
 * previous account's name/photo, and editing them as the new account silently
 * overwrote the first account's saved copy. Namespacing by uid gives each
 * account its own slot, so signing back into an old account restores exactly
 * what that account had saved, independent of whoever used the device between.
 */
object LocalProfileStore {
    private const val PREFS = "kinetix_local_profile"
    private const val KEY_NAME_PREFIX = "display_name_"
    private const val AVATAR_PREFIX = "profile_avatar_"
    private const val AVATAR_SUFFIX = ".jpg"

    /**
     * The account this store is currently scoped to. Falls back to a shared
     * "guest" bucket when nobody is signed in — this screen is normally only
     * reachable while signed in, but every function here stays crash-free and
     * well-defined either way rather than assuming a non-null user.
     */
    private fun uid(): String = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"

    /** The saved name override for the CURRENT account, or null if never set. */
    fun getName(context: Context): String? =
        prefs(context).getString(KEY_NAME_PREFIX + uid(), null)?.takeIf { it.isNotBlank() }

    fun setName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_NAME_PREFIX + uid(), name.trim()).apply()
    }

    /** The CURRENT account's local avatar copy, or null if none was ever saved. */
    fun avatarFile(context: Context): File? =
        avatarFileFor(context, uid()).takeIf { it.exists() }

    /**
     * Copies [source] (a content:// Uri from the system photo picker) into the
     * app's private storage as the new avatar for the CURRENT account, deleting
     * whatever copy that account had saved before first — a change never leaves
     * an old copy behind. Other accounts' saved avatars are untouched.
     */
    fun saveAvatar(context: Context, source: Uri): File {
        val dest = avatarFileFor(context, uid())
        if (dest.exists()) dest.delete()
        context.contentResolver.openInputStream(source)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        return dest
    }

    /**
     * Removes [uid]'s saved local name override and avatar file. Used by
     * account reset/deletion so a wiped account's device-local files don't
     * linger indefinitely and "Reset" actually reverts this screen to defaults
     * — rather than [getName]/[avatarFile]'s "current account" scoping, this
     * takes [uid] explicitly so it always targets the account being erased.
     */
    fun clear(context: Context, uid: String) {
        prefs(context).edit().remove(KEY_NAME_PREFIX + uid).apply()
        avatarFileFor(context, uid).let { if (it.exists()) it.delete() }
    }

    private fun avatarFileFor(context: Context, uid: String): File =
        File(context.filesDir, AVATAR_PREFIX + uid + AVATAR_SUFFIX)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
