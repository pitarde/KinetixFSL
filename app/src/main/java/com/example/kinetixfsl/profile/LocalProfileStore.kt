package com.example.kinetixfsl.profile

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * The user's editable display name and profile photo for the (offline)
 * Profile screen — stored entirely on this device: the name in
 * SharedPreferences, the photo as a private copy under the app's own files
 * directory. Neither ever touches the Firebase account; this only overrides
 * what the Profile screen shows on this phone. See [EditProfileSheet].
 */
object LocalProfileStore {
    private const val PREFS = "kinetix_local_profile"
    private const val KEY_NAME = "display_name"
    private const val AVATAR_FILENAME = "profile_avatar.jpg"

    /** The saved name override, or null if the user has never set one. */
    fun getName(context: Context): String? =
        prefs(context).getString(KEY_NAME, null)?.takeIf { it.isNotBlank() }

    fun setName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_NAME, name.trim()).apply()
    }

    /** The local copy of the chosen avatar, or null if none was ever saved. */
    fun avatarFile(context: Context): File? =
        File(context.filesDir, AVATAR_FILENAME).takeIf { it.exists() }

    /**
     * Copies [source] (a content:// Uri from the system photo picker) into
     * the app's private storage as the new avatar, deleting whatever copy
     * was saved before first — a change never leaves an old copy behind.
     */
    fun saveAvatar(context: Context, source: Uri): File {
        val dest = File(context.filesDir, AVATAR_FILENAME)
        if (dest.exists()) dest.delete()
        context.contentResolver.openInputStream(source)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        return dest
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
