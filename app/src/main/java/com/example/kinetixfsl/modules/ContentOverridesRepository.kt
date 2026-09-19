package com.example.kinetixfsl.modules

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Reads the admin-managed `contentOverrides` collection so the app can honour a
 * module an admin has hidden from the Content Management screen. One document
 * per category id; `disabled == true` means "don't show this module."
 *
 * Fail-open on purpose: if the read errors (offline, rules, etc.) we return an
 * empty set so the full Modules grid still shows — a hidden flag failing to
 * load must never blank out the whole learning catalog.
 */
class ContentOverridesRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun disabledCategoryIds(): Set<String> = runCatching {
        db.collection("contentOverrides").get().await().documents
            .filter { it.getBoolean("disabled") == true }
            .map { it.id }
            .toSet()
    }.getOrDefault(emptySet()).also { cached = it }

    companion object {
        /**
         * The last set of hidden category ids this process has seen, kept at
         * class scope (not per-screen-instance) so leaving the Modules screen
         * and coming back paints with the previous, correct answer right away
         * instead of the full grid — hidden modules included — for the frame
         * or two a fresh Firestore read takes every single time the screen is
         * recomposed. See ModulesScreen's use of this as produceState's
         * initial value.
         */
        @Volatile
        var cached: Set<String>? = null
            private set
    }
}
