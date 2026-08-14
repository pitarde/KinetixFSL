package com.example.kinetixfsl.progress

import com.google.firebase.auth.FirebaseAuth

/**
 * Namespaces all local progress storage per signed-in account, so switching or
 * creating an account starts from a clean slate — no cross-account bleed.
 *
 * Every progress store keys its SharedPreferences file by the current Firebase
 * Auth UID via [prefs]. A signed-out state falls back to a shared "guest" bucket.
 */
object UserScope {

    /** Current account id, or "guest" when signed out. */
    fun uid(): String = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"

    /** A per-account SharedPreferences file name for [base]. */
    fun prefs(base: String): String = "${base}__${uid()}"
}
