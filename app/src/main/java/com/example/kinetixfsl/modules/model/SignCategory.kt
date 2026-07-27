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
            title = "Numbers in Filipino",
            signs = (0..9).map { digit ->
                SignEntry(id = "num_$digit", name = digit.toString())
            },
        ),

        // ── 3. Greetings & Courtesies ────────────────────────────
        SignCategory(
            id = "greetings",
            title = "Greetings & Courtesies",
            signs = listOf(
                SignEntry(id = "greet_kamusta", name = "Kamusta", isDynamic = true),
                SignEntry(id = "greet_welcome", name = "Welcome", isDynamic = true),
                SignEntry(id = "greet_salamat", name = "Salamat", isDynamic = true),
                SignEntry(id = "greet_pakiusap", name = "Pakiusap", isDynamic = true),
            ),
        ),

        // ── 4. Basic Responses ───────────────────────────────────
        SignCategory(
            id = "responses",
            title = "Basic Responses",
            signs = listOf(
                SignEntry(id = "resp_oo", name = "Oo", isDynamic = true),
                SignEntry(id = "resp_hindi", name = "Hindi", isDynamic = true),
                SignEntry(id = "resp_hintay", name = "Hintay", isDynamic = true),
                SignEntry(id = "resp_sige", name = "Sige", isDynamic = true),
            ),
        ),

        // ── 5. Inquiries & Status ────────────────────────────────
        SignCategory(
            id = "inquiries",
            title = "Inquiries & Status",
            signs = listOf(
                SignEntry(id = "inq_magkano", name = "Magkano", isDynamic = true),
                SignEntry(id = "inq_ilan", name = "Ilan", isDynamic = true),
                SignEntry(id = "inq_problema", name = "Problema", isDynamic = true),
                SignEntry(id = "inq_kamusta", name = "Kamusta", isDynamic = true),
            ),
        ),

        // ── 6. Commerce & Transactions ───────────────────────────
        SignCategory(
            id = "commerce",
            title = "Commerce & Transaction",
            signs = listOf(
                SignEntry(id = "com_barya", name = "Barya", isDynamic = true),
                SignEntry(id = "com_cash", name = "Cash", isDynamic = true),
                SignEntry(id = "com_card", name = "Card", isDynamic = true),
                SignEntry(id = "com_resibo", name = "Resibo", isDynamic = true),
            ),
        ),

        // ── 7. Everyday Expressions ──────────────────────────────
        SignCategory(
            id = "everyday",
            title = "Everyday Expression",
            signs = listOf(
                SignEntry(id = "every_discount", name = "Discount", isDynamic = true),
                SignEntry(id = "every_ulit", name = "Ulit", isDynamic = true),
                SignEntry(id = "every_ingat", name = "Ingat", isDynamic = true),
                SignEntry(id = "every_paumanhin", name = "Paumanhin", isDynamic = true),
            ),
        ),
    )

    /** Quick lookup by category id. */
    fun findCategory(categoryId: String): SignCategory? =
        categories.find { it.id == categoryId }
}