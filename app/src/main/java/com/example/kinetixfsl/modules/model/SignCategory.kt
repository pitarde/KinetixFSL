package com.example.kinetixfsl.modules.model

/**
 * A single FSL sign the learner can study and practice.
 *
 * @param id    Unique key used for Room storage and model label lookup
 *              (e.g. "alpha_a", "num_3", "greet_kamusta").
 * @param name  Display label shown in the UI (e.g. "A", "Kamusta").
 * @param steps Ordered instructions for performing this sign.
 *              Empty list means content hasn't been authored yet —
 *              the Learning Room handles this gracefully.
 */
data class SignEntry(
    val id: String,
    val name: String,
    val steps: List<String> = emptyList(),
)

/**
 * One learning module / category shown on the Modules screen grid.
 *
 * @param id    Stable key for navigation and Room lookups (e.g. "alphabet").
 * @param title Human-readable category name shown on the card.
 * @param signs Ordered list of signs within this category.
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
 *
 * Steps are populated progressively during the content-training phase.
 * Signs without steps still show up in the list; the Learning Room
 * displays a "Steps coming soon" placeholder for them.
 */
object FslSignData {

    val categories: List<SignCategory> = listOf(
        // ── 1. Filipino Alphabet (A–Z + Ñ + NG) ─────────────────
        SignCategory(
            id = "alphabet",
            title = "Filipino Alphabet",
            signs = listOf(
                // Sample steps for A and B so the Learning Room isn't empty
                // during development. Real steps will replace these.
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
            ) + ('C'..'Z').map { letter ->
                SignEntry(id = "alpha_${letter.lowercaseChar()}", name = letter.toString())
            } + listOf(
                SignEntry(id = "alpha_enye", name = "Ñ"),
                SignEntry(id = "alpha_ng", name = "NG"),
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
                SignEntry(id = "greet_kamusta", name = "Kamusta"),
                SignEntry(id = "greet_welcome", name = "Welcome"),
                SignEntry(id = "greet_salamat", name = "Salamat"),
                SignEntry(id = "greet_pakiusap", name = "Pakiusap"),
            ),
        ),

        // ── 4. Basic Responses ───────────────────────────────────
        SignCategory(
            id = "responses",
            title = "Basic Responses",
            signs = listOf(
                SignEntry(id = "resp_oo", name = "Oo"),
                SignEntry(id = "resp_hindi", name = "Hindi"),
                SignEntry(id = "resp_hintay", name = "Hintay"),
                SignEntry(id = "resp_sige", name = "Sige"),
            ),
        ),

        // ── 5. Inquiries & Status ────────────────────────────────
        SignCategory(
            id = "inquiries",
            title = "Inquiries & Status",
            signs = listOf(
                SignEntry(id = "inq_magkano", name = "Magkano"),
                SignEntry(id = "inq_ilan", name = "Ilan"),
                SignEntry(id = "inq_problema", name = "Problema"),
                SignEntry(id = "inq_kamusta", name = "Kamusta"),
            ),
        ),

        // ── 6. Commerce & Transactions ───────────────────────────
        SignCategory(
            id = "commerce",
            title = "Commerce & Transaction",
            signs = listOf(
                SignEntry(id = "com_barya", name = "Barya"),
                SignEntry(id = "com_cash", name = "Cash"),
                SignEntry(id = "com_card", name = "Card"),
                SignEntry(id = "com_resibo", name = "Resibo"),
            ),
        ),

        // ── 7. Everyday Expressions ──────────────────────────────
        SignCategory(
            id = "everyday",
            title = "Everyday Expression",
            signs = listOf(
                SignEntry(id = "every_discount", name = "Discount"),
                SignEntry(id = "every_ulit", name = "Ulit"),
                SignEntry(id = "every_ingat", name = "Ingat"),
                SignEntry(id = "every_paumanhin", name = "Paumanhin"),
            ),
        ),
    )

    /** Quick lookup by category id. */
    fun findCategory(categoryId: String): SignCategory? =
        categories.find { it.id == categoryId }
}