package com.example.kinetixfsl.profile

/**
 * Hard-coded sample analytics for the Profile screen.
 *
 * Everything here is placeholder data so the screen can be fully designed and
 * reviewed before the real progress/XP/ranking pipeline exists. The shapes are
 * intentionally close to what a ViewModel will eventually expose (per-category
 * mastery, confusion pairs, decay risk, ...), so wiring real data in later is a
 * swap of the sources — not a redesign of the UI.
 *
 * The four analytics tabs map onto the classic analytics maturity ladder, but
 * users only ever see the friendly labels:
 *   - Progress      → descriptive  (what happened)
 *   - Weak Spots    → diagnostic   (why it happened)
 *   - Forecast      → predictive   (what's likely next)
 *   - Coach's Picks → prescriptive (what to do about it)
 */

// ── Header ──────────────────────────────────────────────────────────

data class ProfileSummary(
    val rankTitle: String,
    val level: Int,
    /** 0f..1f — progress toward the next level. */
    val levelProgress: Float,
    val streakDays: Int,
    val signsLearned: Int,
    val studyMinutes: Int,
    val lessonsCompleted: Int,
)

// ── Tab 1: Progress (descriptive) ───────────────────────────────────

/** One bar in the weekly "signs learned per day" chart. */
data class DayBar(val label: String, val count: Int)

/** Per-category mastery, 0f..1f. */
data class CategoryMastery(val name: String, val percent: Float)

// ── Tab 2: Weak Spots (diagnostic) ──────────────────────────────────

/** A pair of signs the learner tends to mix up. */
data class ConfusionPair(val first: String, val second: String, val confusedPercent: Int)

/** Lessons started vs. finished for one module — drives the drop-off bars. */
data class ModuleDropOff(val module: String, val started: Int, val completed: Int)

/** Error-type split (should sum to 1f) for the segmented error bar. */
data class ErrorBreakdown(val handshape: Float, val motion: Float, val timing: Float)

// ── Tab 3: Forecast (predictive) ────────────────────────────────────

/** A sign that hasn't been practiced recently and risks being forgotten. */
data class DecayItem(val sign: String, val daysSince: Int, val risk: Float)

// ── Tab 4: Coach's Picks (prescriptive) ─────────────────────────────

/** One auto-suggested item in today's recommended practice queue. */
data class QueueItem(val title: String, val reason: String)

enum class Trend { UP, DOWN }

/** A lesson whose difficulty will adapt, with the reason why. */
data class AdaptiveItem(val module: String, val trend: Trend, val reason: String)

// ── Sample data ─────────────────────────────────────────────────────

internal object SampleProfile {

    val summary = ProfileSummary(
        rankTitle = "Rising Signer",
        level = 4,
        levelProgress = 0.62f,
        streakDays = 5,
        signsLearned = 34,
        studyMinutes = 128,
        lessonsCompleted = 12,
    )

    // Tab 1 — Progress
    val weeklyBars = listOf(
        DayBar("Mon", 4),
        DayBar("Tue", 7),
        DayBar("Wed", 3),
        DayBar("Thu", 8),
        DayBar("Fri", 6),
        DayBar("Sat", 2),
        DayBar("Sun", 5),
    )

    val categoryMastery = listOf(
        CategoryMastery("Alphabet", 0.82f),
        CategoryMastery("Numbers", 0.64f),
        CategoryMastery("Greetings", 0.45f),
        CategoryMastery("Family", 0.28f),
        CategoryMastery("Emotions", 0.15f),
    )

    /**
     * 5 weeks × 7 days practice intensity, oldest week first. Values 0..4 map to
     * heatmap shades (0 = no practice). Read row = week, column = Mon..Sun.
     */
    val heatmap: List<List<Int>> = listOf(
        listOf(0, 1, 2, 1, 0, 3, 2),
        listOf(1, 2, 0, 2, 3, 1, 0),
        listOf(2, 3, 3, 1, 2, 0, 1),
        listOf(0, 2, 4, 3, 2, 1, 3),
        listOf(1, 3, 2, 4, 3, 0, 2),
    )

    // Tab 2 — Weak Spots
    val confusionPairs = listOf(
        ConfusionPair("M", "N", 68),
        ConfusionPair("A", "S", 54),
        ConfusionPair("U", "V", 41),
        ConfusionPair("K", "P", 33),
    )

    val dropOffs = listOf(
        ModuleDropOff("Alphabet", started = 28, completed = 23),
        ModuleDropOff("Numbers", started = 20, completed = 13),
        ModuleDropOff("Greetings", started = 16, completed = 7),
        ModuleDropOff("Family", started = 12, completed = 4),
    )

    val errorBreakdown = ErrorBreakdown(handshape = 0.52f, motion = 0.31f, timing = 0.17f)

    // Tab 3 — Forecast
    /** Cumulative signs learned so far, one point per week (actual). */
    val forecastActual = listOf(8, 19, 34)

    /**
     * Projected cumulative signs, continuing from the last actual point so the
     * dashed line joins the solid one cleanly.
     */
    val forecastProjected = listOf(34, 47, 58, 70)

    val streakRiskPercent = 38
    val dropOffWindow = "Saturdays after 8 PM"

    val decayWatch = listOf(
        DecayItem("Salamat", daysSince = 9, risk = 0.86f),
        DecayItem("Letter J", daysSince = 7, risk = 0.71f),
        DecayItem("Number 8", daysSince = 5, risk = 0.55f),
        DecayItem("Kamusta", daysSince = 4, risk = 0.42f),
    )

    // Tab 4 — Coach's Picks
    val recommendedQueue = listOf(
        QueueItem("Review: Salamat", "Highest forget-risk — 9 days since practice"),
        QueueItem("Drill: M vs N", "Your most-confused pair at 68%"),
        QueueItem("Finish: Greetings", "Only 7 of 16 lessons completed"),
    )

    val fixWeakSpotStat = "You confuse M and N 68% of the time."
    val fixWeakSpotCta = "Start 3-min drill"

    val bestTimeInsight = "Your accuracy is 23% higher on weekday mornings than at night."

    val adaptiveLessons = listOf(
        AdaptiveItem("Alphabet", Trend.UP, "82% mastery — adding faster, timed rounds"),
        AdaptiveItem("Family", Trend.DOWN, "Low accuracy — slowing down with more guided reps"),
    )
}
