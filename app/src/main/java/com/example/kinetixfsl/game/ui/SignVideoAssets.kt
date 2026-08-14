package com.example.kinetixfsl.game.ui

import android.content.Context

/**
 * Resolves a sign's bundled tutorial video, if one has been added yet.
 *
 * Convention: a sign's clip lives in `res/raw` named exactly by its sign id —
 * e.g. sign `alpha_a` → `res/raw/alpha_a.mp4`. Resource names are looked up by
 * that id, so **adding a video later is drop-in**: name the file `<signId>.mp4`,
 * put it in `res/raw`, rebuild — the quiz picks it up with no code change.
 *
 * Right now only `alpha_a.mp4` exists, so Letter A plays a real clip and every
 * other sign falls back to [VideoPlaceholderCard]. See `docs/QUIZ_GAME.md`.
 */
object SignVideoAssets {

    /** Cache resource-id lookups; getIdentifier() is reflection-ish and not free. */
    private val cache = HashMap<String, Int>()

    /** The `R.raw.<signId>` resource id, or 0 if no clip has been bundled yet. */
    fun rawResId(context: Context, signId: String): Int = cache.getOrPut(signId) {
        context.resources.getIdentifier(signId, "raw", context.packageName)
    }

    fun hasVideo(context: Context, signId: String): Boolean = rawResId(context, signId) != 0
}
