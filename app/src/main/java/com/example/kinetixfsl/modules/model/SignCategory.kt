package com.example.kinetixfsl.modules.model

/**
 * A single FSL sign the learner can study and practice.
 *
 * @param id        Unique key used for Room storage and model label lookup.
 * @param name      Display label shown in the UI (e.g. "A", "Kamusta").
 * @param steps     Ordered instructions for performing this sign.
 * @param isDynamic True for signs that involve hand movement (J, Z, Ñ, NG,
 *                  and all word signs). False for static hand poses.
 *                  The Camera Practice screen uses this to pick the right
 *                  classifier (Dense for static, LSTM for dynamic).
 */
data class SignEntry(
    val id: String,
    val name: String,
    val steps: List<String> = emptyList(),
    val isDynamic: Boolean = false,
)

/**
 * One learning module / category shown on the Modules screen grid.
 */
data class SignCategory(
    val id: String,
    val title: String,
    val signs: List<SignEntry>,
) {
    val signCount: Int get() = signs.size
}

/**
 * All FSL categories and their signs — hardcoded for now.
 *
 * Total: 28 alphabet + 10 numbers + 20 word signs = 58 signs.
 */
object FslSignData {

    val categories: List<SignCategory> = listOf(
        // ── 1. Filipino Alphabet (A–Z + Ñ + NG) ─────────────────
        SignCategory(
            id = "alphabet",
            title = "Filipino Alphabet",
            signs = listOf(
                SignEntry(
                    id = "alpha_a",
                    name = "A",
                    steps = listOf(
                        "Make a fist with your dominant hand.",
                        "Keep your thumb resting on the side of your index finger.",
                        "Face your fist forward, knuckles away from you.",
                    ),
                ),
                SignEntry(
                    id = "alpha_b",
                    name = "B",
                    steps = listOf(
                        "Extend hand forward, palm facing out.",
                        "Keep all four fingers straight and together, pointing up.",
                        "Fold your thumb across your palm.",
                    ),
                ),
            ) + ('C'..'I').map { letter ->
                SignEntry(id = "alpha_${letter.lowercaseChar()}", name = letter.toString())
            } + listOf(
                // J is dynamic — traces a curve
                SignEntry(id = "alpha_j", name = "J", isDynamic = true),
            ) + ('K'..'Y').map { letter ->
                SignEntry(id = "alpha_${letter.lowercaseChar()}", name = letter.toString())
            } + listOf(
                // Z is dynamic — draws a Z shape
                SignEntry(id = "alpha_z", name = "Z", isDynamic = true),
                // Ñ is dynamic — involves a wave motion
                SignEntry(id = "alpha_enye", name = "Ñ", isDynamic = true),
                // NG is dynamic — hand shape transition
                SignEntry(id = "alpha_ng", name = "NG", isDynamic = true),
            ),
        ),

        // ── 2. Numbers 0–9 ──────────────────────────────────────
        SignCategory(
            id = "numbers",
            title = "Numbers 0-9",
            signs = listOf(
                SignEntry(
                    id = "num_0",
                    name = "0",
                    steps = listOf(
                        "Curve your fingers into a circle shape.",
                        "Touch the tip of your thumb to the tip of your middle finger.",
                        "Hold the sign up with the palm facing outward.",
                    ),
                ),
                SignEntry(
                    id = "num_1",
                    name = "1",
                    steps = listOf(
                        "Make a fist with your dominant hand.",
                        "Extend only your index finger, pointing up.",
                        "Keep your palm facing toward you.",
                    ),
                ),
                SignEntry(
                    id = "num_2",
                    name = "2",
                    steps = listOf(
                        "Make a fist with your dominant hand.",
                        "Extend your index and middle fingers, spread apart.",
                        "Keep your palm facing toward you.",
                    ),
                ),
                SignEntry(
                    id = "num_3",
                    name = "3",
                    steps = listOf(
                        "Make a fist with your dominant hand.",
                        "Extend your thumb, index, and middle fingers, spread apart.",
                        "Keep your palm facing toward you.",
                    ),
                ),
                SignEntry(
                    id = "num_4",
                    name = "4",
                    steps = listOf(
                        "Make a fist with your dominant hand.",
                        "Extend all four fingers (index, middle, ring, pinky) spread apart.",
                        "Keep your thumb folded across your palm.",
                        "Palm faces toward you.",
                    ),
                ),
                SignEntry(
                    id = "num_5",
                    name = "5",
                    steps = listOf(
                        "Open your hand fully with all five fingers spread apart.",
                        "Keep your palm facing toward you.",
                    ),
                ),
                SignEntry(
                    id = "num_6",
                    name = "6",
                    steps = listOf(
                        "Hold your hand up with your palm facing outward.",
                        "Fold your pinky finger down to touch the tip of your thumb.",
                        "Keep your remaining three fingers (index, middle, ring) extended and spread.",
                    ),
                ),
                SignEntry(
                    id = "num_7",
                    name = "7",
                    steps = listOf(
                        "Hold your hand up with your palm facing outward.",
                        "Fold your ring finger down to touch the tip of your thumb.",
                        "Keep your remaining three fingers (index, middle, pinky) extended and spread.",
                    ),
                ),
                SignEntry(
                    id = "num_8",
                    name = "8",
                    steps = listOf(
                        "Hold your hand up with your palm facing outward.",
                        "Fold your middle finger down to touch the tip of your thumb.",
                        "Keep your remaining three fingers (index, ring, pinky) extended and spread.",
                    ),
                ),
                SignEntry(
                    id = "num_9",
                    name = "9",
                    steps = listOf(
                        "Hold your hand up with your palm facing outward.",
                        "Fold your index finger down to touch the tip of your thumb.",
                        "Keep your remaining three fingers (middle, ring, pinky) extended and spread.",
                    ),
                ),
            ),
        ),

        // ── 3. Greetings & Courtesies ────────────────────────────
        SignCategory(
            id = "greetings",
            title = "Greetings",
            signs = listOf(
                SignEntry(id = "greet_kamusta", name = "Kamusta", isDynamic = true),
                SignEntry(id = "greet_salamat", name = "Salamat", isDynamic = true),
                SignEntry(id = "greet_walang_anuman", name = "Walang Anuman", isDynamic = true),
                SignEntry(id = "greet_ayos_lang_ako", name = "Ayos lang ako", isDynamic = true),
            ),
        ),

        // ── 4. School ────────────────────────────────────────────
        SignCategory(
            id = "school",
            title = "School",
            // Steps intentionally left blank for all signs — video-based step
            // guidance will be added after all categories are trained.
            signs = listOf(
                SignEntry(id = "school_pagaaral", name = "Pag-aaral", isDynamic = true),
                SignEntry(id = "school_basahin", name = "Basahin", isDynamic = true),
                SignEntry(id = "school_paaralan", name = "Paaralan", isDynamic = true),
                SignEntry(id = "school_magaaral", name = "Mag-aaral", isDynamic = true),
            ),
        ),

        // ── 5. Emergency ──────────────────────────────────────────
        SignCategory(
            id = "emergency",
            title = "Emergency",
            // Steps intentionally left blank — video-based step guidance
            // will be added after all categories are trained.
            signs = listOf(
                SignEntry(id = "emer_danger", name = "Danger", isDynamic = true),
                SignEntry(id = "emer_stop", name = "Stop", isDynamic = true),
                SignEntry(id = "emer_calm_down", name = "Calm down", isDynamic = true),
                SignEntry(id = "emer_accident", name = "Accident", isDynamic = true),
            ),
        ),

        // ── 6. Daily Needs ────────────────────────────────────────
        SignCategory(
            id = "dailyneeds",
            title = "Daily Needs",
            // Steps intentionally left blank — video-based step guidance
            // will be added after all categories are trained.
            signs = listOf(
                SignEntry(id = "dn_eat", name = "Eat", isDynamic = true),
                SignEntry(id = "dn_drink", name = "Drink", isDynamic = true),
                SignEntry(id = "dn_sleep", name = "Sleep", isDynamic = true),
                SignEntry(id = "dn_hungry", name = "Hungry", isDynamic = true),
            ),
        ),

        // ── 7. Social Interaction ─────────────────────────────────
        SignCategory(
            id = "social",
            title = "Social Interaction",
            // Steps intentionally left blank — video-based step guidance
            // will be added after all categories are trained.
            signs = listOf(
                SignEntry(id = "soc_oo", name = "Oo", isDynamic = true),
                SignEntry(id = "soc_hindi", name = "Hindi", isDynamic = true),
                SignEntry(id = "soc_kaibigan", name = "Kaibigan", isDynamic = true),
                SignEntry(id = "soc_patawad", name = "Patawad", isDynamic = true),
            ),
        ),
    )

    /** Quick lookup by category id. */
    fun findCategory(categoryId: String): SignCategory? =
        categories.find { it.id == categoryId }
}