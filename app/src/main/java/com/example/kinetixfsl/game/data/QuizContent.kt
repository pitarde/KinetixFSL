package com.example.kinetixfsl.game.data

import com.example.kinetixfsl.game.model.QuizSign
import com.example.kinetixfsl.game.model.Tier
import com.example.kinetixfsl.modules.model.FslSignData

/**
 * The static, bundled question banks — one per tier — drawn entirely from the
 * app's existing [FslSignData] so there's a single source of truth for sign
 * ids/names. This is the "offline-first" content: nothing here is read from
 * Firestore during play.
 *
 * The three banks are disjoint (no sign appears in two tiers) and sized exactly
 * to `tier.levels × SIGNS_PER_LEVEL` — 15 / 15 / 20 = 50 signs total. They're
 * ordered roughly easy → hard within the game as a whole.
 *
 * To grow a bank later, just add more sign ids below (they must exist in
 * FslSignData). To give any sign a real video, drop `<signId>.mp4` into
 * `res/raw` — see `docs/QUIZ_GAME.md` and SignVideoAssets.
 */
object QuizContent {

    /**
     * Tagalog display words for the quiz, keyed by sign id. Applied only to the
     * quiz's on-screen labels — the underlying [FslSignData] `name` (and thus the
     * TFLite classifier labels used by Camera Practice) stays English, so sign
     * detection is unaffected. Add more entries here to localise other signs.
     */
    private val displayOverrides: Map<String, String> = mapOf(
        // Emergency
        "emer_danger" to "Panganib",
        "emer_stop" to "Tigil",
        "emer_calm_down" to "Kalma",
        "emer_accident" to "Aksidente",
        // Daily Needs
        "dn_eat" to "Kain",
        "dn_drink" to "Inom",
        "dn_sleep" to "Tulog",
        "dn_hungry" to "Gutom",
    )

    /** Every sign in the app, keyed by id, so banks can be built by id alone. */
    private val allSigns: Map<String, QuizSign> =
        FslSignData.categories.flatMap { category ->
            category.signs.map { entry ->
                QuizSign(
                    id = entry.id,
                    word = displayOverrides[entry.id] ?: entry.name,
                    categoryId = category.id,
                )
            }
        }.associateBy { it.id }

    private fun bankOf(vararg ids: String): List<QuizSign> = ids.map { id ->
        allSigns[id] ?: error("QuizContent: unknown sign id '$id' — not in FslSignData")
    }

    /** Easy (Levels 1–3): greetings, social basics, small numbers, two daily needs. */
    val easyBank: List<QuizSign> = bankOf(
        "greet_kamusta", "greet_salamat", "greet_walang_anuman", "greet_ayos_lang_ako",
        "soc_oo", "soc_hindi", "soc_kaibigan", "soc_patawad",
        "num_1", "num_2", "num_3", "num_4", "num_5",
        "dn_eat", "dn_drink",
    )

    /** Medium (Levels 4–6): the rest of daily needs, school, larger numbers, A–D. */
    val mediumBank: List<QuizSign> = bankOf(
        "dn_sleep", "dn_hungry",
        "school_pagaaral", "school_basahin", "school_paaralan", "school_magaaral",
        "num_0", "num_6", "num_7", "num_8", "num_9",
        "alpha_a", "alpha_b", "alpha_c", "alpha_d",
    )

    /** Hard (Levels 7–10): emergency vocabulary and alphabet E–T. */
    val hardBank: List<QuizSign> = bankOf(
        "emer_danger", "emer_stop", "emer_calm_down", "emer_accident",
        "alpha_e", "alpha_f", "alpha_g", "alpha_h", "alpha_i", "alpha_j",
        "alpha_k", "alpha_l", "alpha_m", "alpha_n", "alpha_o", "alpha_p",
        "alpha_q", "alpha_r", "alpha_s", "alpha_t",
    )

    fun bank(tier: Tier): List<QuizSign> = when (tier) {
        Tier.EASY -> easyBank
        Tier.MEDIUM -> mediumBank
        Tier.HARD -> hardBank
    }

    /** Look up a sign by id (used to render tutorial videos and match answers). */
    fun sign(id: String): QuizSign? = allSigns[id]

    /** A sign's display word, or the id itself as a last-resort fallback. */
    fun word(id: String): String = allSigns[id]?.word ?: id

    init {
        // Fail fast in debug if a bank is ever mis-sized against its tier.
        Tier.entries.forEach { tier ->
            val size = bank(tier).size
            require(size == tier.bankSize) {
                "QuizContent: ${tier.name} bank has $size signs, expected ${tier.bankSize}"
            }
        }
    }
}
